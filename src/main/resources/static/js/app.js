/* ShopSphere frontend — real Spring Boot API integration.
 * Deploy these files under src/main/resources/static to avoid CORS.
 * Security is enforced by Spring Security, not by hiding navigation buttons.
 */
(() => {
  'use strict';

  const API = '/api';
  const TOKEN_KEY = 'shopsphere_jwt';
  const EMAIL_STATUS_PATH = '/user/email-status'; // Implement GET /api/user/email-status => { emailVerified: boolean }
  const OTP_COOLDOWN_MS = 60_000;
  const CATEGORIES = [
    ['ELECTRONICS', 'Electronics', '💻'], ['CLOTHING', 'Fashion', '👟'],
    ['HOME_AND_KITCHEN', 'Home & living', '🏡'], ['BOOKS', 'Books', '📚'],
    ['SPORTS_AND_OUTDOORS', 'Sports', '🏸'], ['BEAUTY_AND_PERSONAL_CARE', 'Beauty', '✨'],
    ['TOYS_AND_GAMES', 'Toys & games', '🧩'], ['AUTOMOTIVE', 'Automotive', '🚙'],
    ['HEALTH_AND_WELLNESS', 'Health', '🌿'], ['FOOD_AND_BEVERAGES', 'Food & drink', '☕'],
    ['JEWELRY_AND_ACCESSORIES', 'Jewelry', '💍'], ['PET_SUPPLIES', 'Pets', '🐾'],
    ['OFFICE_PRODUCTS', 'Office', '🖊'], ['MUSICAL_INSTRUMENTS', 'Music', '🎸'],
    ['ART_AND_CRAFT', 'Art & craft', '🎨'], ['BABY_PRODUCTS', 'Baby', '🧸'],
    ['GROCERY', 'Grocery', '🥑'], ['OTHER', 'Everything else', '✳']
  ];
  const ROUTES = new Set(['home','shop','deals','cart','checkout','orders','account','seller','admin']);
  const state = {
    token: localStorage.getItem(TOKEN_KEY) || '',
    email: '', role: 'GUEST', seller: null, products: [],
    cart: null, addresses: [], ordersPage: 0, catalogPage: 0,
    catalogQuery: '', category: '', currentRoute: 'home',
    currentOrder: null, adminsChecked: false, bannerIndex: 0,
    emailVerified: null, emailStatusLoading: false, emailStatusRequest: 0,
    emailOtpSending: false, resetOtpSending: false,
    profile: null
  };
  const el = id => document.getElementById(id);
  const esc = input => String(input ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const money = value => new Intl.NumberFormat('en-IN', {style: 'currency', currency: 'INR', maximumFractionDigits: 2}).format(Number(value) || 0);
  const humanDate = date => date ? new Date(date).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}) : '';
  const labelFor = (value) => CATEGORIES.find(c => c[0] === value)?.[1] || String(value || 'Other').replaceAll('_', ' ').toLowerCase();
  const categoryIcon = value => CATEGORIES.find(c => c[0] === value)?.[2] || '✳';
  const inStock = p => p?.status === 'AVAILABLE' && Number(p?.stockQuantity || 0) > 0;
  const discountOf = p => { const prev = Number(p?.compareAtPrice); const now = Number(p?.price); return prev > now && now > 0 ? Math.round((prev-now)/prev*100) : 0; };
  const safeImage = value => { const v=String(value||'').trim(); if(!v)return '';
    // Accept absolute URLs (http/https), /uploads/ paths, and protocol-relative URLs.
    if(/^https?:\/\//i.test(v)||/^\/uploads\//.test(v)||/^\/\//.test(v))return v;
    // If it looks like a domain with an extension, treat as https://
    if(/^[a-zA-Z0-9][-a-zA-Z0-9.]*\.[a-zA-Z]{2,}(\/|$)/.test(v))return 'https://'+v;
    return ''; };
  const isLoggedIn = () => Boolean(state.token);
  const isAdmin = () => state.role === 'ADMIN';
  const isSeller = () => state.role === 'SELLER' && state.seller?.status === 'APPROVED';
  const isBuyer = () => state.role === 'BUYER';

  function toast(message, type='info') {
    const node=document.createElement('div');
    node.className='toast '+type;
    node.textContent=String(message || 'Something went wrong');
    // Native <dialog> elements live in the browser's top layer. A toast placed in
    // <body>, regardless of z-index, appears BEHIND an open modal dialog.
    const activeDialog=Array.from(document.querySelectorAll('dialog[open]')).at(-1);
    let host=el('toastStack');
    if(activeDialog){
      host=activeDialog.querySelector('.dialog-toast-stack');
      if(!host){
        host=document.createElement('div');
        host.className='dialog-toast-stack';
        host.setAttribute('role','status');
        host.setAttribute('aria-live','polite');
        activeDialog.appendChild(host);
      }
    }
    host.appendChild(node);
    window.setTimeout(()=>node.remove(),4600);
  }

  function showLoginError(message){
    const box=el('loginError');
    box.textContent=message;
    box.classList.remove('hidden');
    el('loginPassword').setAttribute('aria-invalid','true');
  }
  function clearLoginError(){
    el('loginError').textContent='';
    el('loginError').classList.add('hidden');
    el('loginPassword').removeAttribute('aria-invalid');
  }

  function renderEmailVerification(){
    const verified=state.emailVerified===true;
    const checking=state.emailStatusLoading && state.emailVerified===null;
    el('emailVerifiedBadge').classList.toggle('hidden',!verified);
    el('emailVerificationLoading').classList.toggle('hidden',!checking);
    el('emailVerificationControls').classList.toggle('hidden',verified || checking);
    updateOtpButtons();
  }

  // Per-email, per-purpose UX cooldown. Server-side throttling is still needed
  // to prevent abuse from multiple devices or modified clients.
  function otpCooldownKey(purpose,email){
    return `shopsphere_otp_cooldown_${purpose}_${String(email||'').trim().toLowerCase()}`;
  }
  function otpSecondsRemaining(purpose,email){
    if(!email)return 0;
    try{
      const timestamp=Number(localStorage.getItem(otpCooldownKey(purpose,email))||0);
      const remaining=Math.max(0,Math.ceil((timestamp-Date.now())/1000));
      if(!remaining && timestamp)localStorage.removeItem(otpCooldownKey(purpose,email));
      return remaining;
    }catch{return 0;}
  }
  function startOtpCooldown(purpose,email){
    try{localStorage.setItem(otpCooldownKey(purpose,email),String(Date.now()+OTP_COOLDOWN_MS));}
    catch{ /* Local storage may be unavailable; in-flight protection still works. */ }
    updateOtpButtons();
  }
  function updateOtpButtons(){
    const definitions=[
      {id:'sendEmailOtpBtn',purpose:'verify',email:state.email,busy:state.emailOtpSending,label:'Send code'},
      {id:'requestResetBtn',purpose:'reset',email:el('resetEmail').value.trim(),busy:state.resetOtpSending,label:'Send recovery code'}
    ];
    for(const def of definitions){
      const button=el(def.id);
      const remaining=otpSecondsRemaining(def.purpose,def.email);
      button.disabled=def.busy || remaining>0 || (def.purpose==='verify' && state.emailVerified===true);
      if(def.busy){
        button.innerHTML='<span class="otp-hourglass" aria-hidden="true">⏳</span> Sending code…';
        button.setAttribute('aria-label','Sending verification code');
      }else if(remaining>0){
        button.innerHTML=`<span class="otp-hourglass" aria-hidden="true">⏳</span> Resend in ${remaining}s`;
        button.setAttribute('aria-label',`You can request another code in ${remaining} seconds`);
      }else{
        button.textContent=def.label;
        button.removeAttribute('aria-label');
      }
    }
  }

  async function refreshEmailStatus(){
    if(!state.token)return;
    const currentToken=state.token;
    const requestId=++state.emailStatusRequest;
    state.emailStatusLoading=true;
    renderEmailVerification();
    try{
      // Backend response contract: GET /api/user/email-status -> {emailVerified:true|false}.
      const result=await api(EMAIL_STATUS_PATH);
      if(currentToken!==state.token || requestId!==state.emailStatusRequest)return;
      const verified=typeof result==='boolean' ? result : result?.emailVerified;
      if(typeof verified!=='boolean')throw new Error('Unexpected email status response');
      state.emailVerified=verified;
    }catch(err){
      if(currentToken!==state.token || requestId!==state.emailStatusRequest)return;
      // Gracefully retain verification UI until the user adds this backend endpoint.
      if(err.status!==404)console.info('Could not refresh email verification status:',err.message);
    }finally{
      if(currentToken===state.token && requestId===state.emailStatusRequest){
        state.emailStatusLoading=false;
        renderEmailVerification();
      }
    }
  }

  async function sendVerificationOtp(){
    if(!isLoggedIn() || state.emailVerified===true || state.emailOtpSending)return;
    const email=state.email;
    if(otpSecondsRemaining('verify',email)>0)return;
    state.emailOtpSending=true;
    updateOtpButtons();
    try{
      await api('/user/email-verification');
      startOtpCooldown('verify',email);
      toast('Verification code sent. Check your inbox.','success');
    }catch(err){
      if(err.status===429)startOtpCooldown('verify',email);
      toast(err.message,'error');
    }finally{
      state.emailOtpSending=false;
      updateOtpButtons();
    }
  }

  async function verifyEmailOtp(){
    const otp=el('emailOtp').value.trim();
    if(state.emailVerified===true)return;
    if(!/^\d{8}$/.test(otp)){toast('Enter the 8-digit code from your email.','info');return;}
    const button=el('verifyEmailOtpBtn');
    button.disabled=true;
    try{
      await api('/user/verify-otp',{method:'POST',body:{otp}});
      ++state.emailStatusRequest; // Discard any older status request.
      state.emailStatusLoading=false;
      state.emailVerified=true;
      el('emailOtp').value='';
      renderEmailVerification();
      toast('Your email has been verified!','success');
    }catch(err){toast(err.message,'error');}
    finally{button.disabled=false;}
  }
  const status = (label) => `<span class="status-badge ${esc(String(label).toLowerCase())}">${esc(String(label||'').replaceAll('_',' '))}</span>`;
  const empty = (title,detail='',action='') => `<div class="empty-state"><span class="empty-icon">✳</span><h3>${esc(title)}</h3><p>${esc(detail)}</p>${action}</div>`;
  const imageFor = (p, css='') => safeImage(p?.imageUrl||p?.productImageUrl) ? `<img ${css} src="${safeImage(p?.imageUrl||p?.productImageUrl)}" alt="${esc(p?.name||p?.productName||'Product')}" loading="lazy">` : `<div class="media-fallback" aria-hidden="true">${categoryIcon(p?.category)}</div>`;

  class ApiError extends Error { constructor(message,code,body) { super(message); this.status=code; this.body=body; } }
  async function api(path, {method='GET',body,auth=true}={}) {
    const headers={Accept:'application/json'};
    if (body !== undefined) headers['Content-Type']='application/json';
    if (auth && state.token) headers.Authorization=`Bearer ${state.token}`;
    let res;
    try { res=await fetch(`${API}${path}`,{method,headers,body:body===undefined?undefined:JSON.stringify(body)}); }
    catch { throw new ApiError('Cannot reach the store right now. Check that Spring Boot is running.',0,null); }
    const raw=await res.text();let value=raw;
    try { value=raw ? JSON.parse(raw) : null; } catch {}
    if (!res.ok) {
      let message=typeof value==='object' && value ? (value.message || value.detail || value.error || value.title) : raw;
      message=(String(message||`Request failed (${res.status})`)).replace(/<[^>]*>/g,'').slice(0,250);
      throw new ApiError(message,res.status,value);
    }
    return value;
  }
  function jwtEmail(token) {
    try { const part=token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'); const payload=JSON.parse(atob(part.padEnd(Math.ceil(part.length/4)*4,'='))); return payload.sub || ''; }
    catch { return ''; }
  }
  function setSession(token) {
    state.token=token||''; state.email=token?jwtEmail(token):'';
    if (token) localStorage.setItem(TOKEN_KEY,token); else localStorage.removeItem(TOKEN_KEY);
    ++state.emailStatusRequest;
    state.emailVerified=null; state.emailStatusLoading=false; state.emailOtpSending=false;
    if (!token){state.role='GUEST';state.seller=null;state.cart=null;state.addresses=[];state.adminsChecked=false;state.profile=null;}
    updateIdentity();
    renderEmailVerification();
  }
  function updateIdentity() {
    el('accountLabel').textContent=isLoggedIn()?state.email.split('@')[0]||'My account':'Sign in';
    el('accountAvatar').textContent=isLoggedIn()?(state.email[0]||'S').toUpperCase():'↗';
    el('profileEmail').textContent=state.email||'Welcome to ShopSphere';
    el('profileName').textContent=isLoggedIn()?state.email.split('@')[0]||'My account':'Your account';
    el('profileAvatar').textContent=isLoggedIn()?(state.email[0]||'S').toUpperCase():'S';
    el('profileRole').textContent=({ADMIN:'Administrator',SELLER:'Seller',BUYER:'Customer',GUEST:'Guest'})[state.role]||'Customer';
    el('adminNav').classList.toggle('hidden',!isAdmin());
    el('sellerNav').classList.toggle('hidden',!isSeller());
    el('accountAddressPanel').classList.toggle('hidden',!isBuyer());
    el('sellerApplicationPanel').classList.toggle('hidden',!isBuyer() && !isSeller());
    el('ordersHeaderBtn').classList.toggle('hidden',!isBuyer());
    el('cartCount').textContent=String(state.cart?.totalItems||0);
  }
  async function hydrateSession() {
    if (!state.token) {setSession(''); return;}
    const validPattern=/^[\w-]+\.[\w-]+\.[\w-]+$/;
    if (!validPattern.test(state.token)){setSession('');return;}
    // /cart is an authenticated endpoint. The existing backend does not yet expose /user/me.
    try {state.cart=await api('/cart');}
    catch(e){if(e.status===401){setSession('');toast('Your session expired. Please sign in again.','info');return;} console.warn('Cart unavailable during sign-in check',e);}
    state.email=jwtEmail(state.token);
    state.role='BUYER';
    try {
      // Only actual administrators can call this protected endpoint.
      const pending=await api('/admin/sellers/pending');
      if (Array.isArray(pending)) {state.role='ADMIN';state.adminsChecked=true;}
    } catch(e) {if(e.status!==403 && e.status!==401)console.info('Admin role probe unavailable',e.message);}
    if (state.role!=='ADMIN') {
      try {const seller=await api('/seller/me');if(seller){state.seller=seller; if(seller.status==='APPROVED')state.role='SELLER';}}
      catch(e){if(e.status!==404 && e.status!==403)console.info('Seller status unavailable',e.message);}
    }
    updateIdentity();
    await refreshEmailStatus();
  }

  function openAuth(tab='login') {
    setAuthTab(tab);
    if(!el('authDialog').open)el('authDialog').showModal();
  }
  function setAuthTab(tab) {
    clearLoginError();
    document.querySelectorAll('[data-auth-tab]').forEach(btn=>btn.classList.toggle('active',btn.dataset.authTab===tab));
    document.querySelectorAll('[data-auth-panel]').forEach(form=>form.classList.toggle('hidden',form.dataset.authPanel!==tab));
  }
  function closeDialog(id) {const dlg=el(id); if(dlg?.open)dlg.close();}
  function requireAuth(){if(!isLoggedIn()){openAuth();toast('Sign in to continue','info');return false;}return true;}
  function pageName(hash=window.location.hash) {const path=hash.replace(/^#\/?/,'').split(/[?\/]/)[0].trim();return ROUTES.has(path)?path:'home';}
  function route(page) {
    if (!ROUTES.has(page))page='home';
    if (['cart','checkout','orders','account','seller','admin'].includes(page) && !requireAuth())return;
    if (page==='admin'&&!isAdmin()){toast('This page is for administrators only.','error');return;}
    if (page==='seller'&&!isSeller()){toast('Seller Studio is available after admin approval.','error');return;}
    if (window.location.hash!==`#/${page}`)window.location.hash=`#/${page}`;
    else renderRoute();
  }
  function renderRoute(){
    let page=pageName();
    if(['cart','checkout','orders','account','seller','admin'].includes(page)&&!isLoggedIn())page='home';
    if(page==='admin'&&!isAdmin())page='home';
    if(page==='seller'&&!isSeller())page='home';
    state.currentRoute=page;
    document.querySelectorAll('.page').forEach(node=>node.classList.toggle('active',node.id===`page-${page}`));
    document.querySelectorAll('[data-route].nav-link').forEach(node=>node.classList.toggle('selected',node.dataset.route===page));
    closeCategoryDropdown();
    el('searchSuggestions').classList.add('hidden');
    window.scrollTo({top:0,behavior:'instant'});
    if(page==='home')renderHome();
    if(page==='shop')renderCatalog();
    if(page==='deals')renderDeals();
    if(page==='cart')loadCart();
    if(page==='checkout')prepareCheckout();
    if(page==='orders')loadOrders(0);
    if(page==='account')loadAccount();
    if(page==='seller')renderSeller();
    if(page==='admin')loadAdmin();
  }
  function renderCategories() {
    const main=CATEGORIES.slice(0,6);
    el('categoryTiles').innerHTML=main.map(c=>`<button class="category-tile" data-category="${c[0]}"><span class="category-symbol">${c[2]}</span><strong>${esc(c[1])}</strong></button>`).join('');
    el('dropdownCategories').innerHTML=CATEGORIES.map(c=>`<button type="button" data-category="${c[0]}"><span>${c[2]}</span>${esc(c[1])}</button>`).join('');
    for(const select of [el('filterCategory'),el('pCategory')]){
      select.insertAdjacentHTML('beforeend',CATEGORIES.map(c=>`<option value="${c[0]}">${esc(c[1])}</option>`).join(''));
    }
    el('footerYear').textContent=new Date().getFullYear();
  }
  function closeCategoryDropdown(){el('categoryDropdown').classList.add('hidden');el('categoryToggle').setAttribute('aria-expanded','false');}
  function selectCategory(category){state.category=category;state.catalogPage=0;el('filterCategory').value=category;state.catalogQuery='';el('globalSearch').value='';closeCategoryDropdown();route('shop');}
  async function loadProducts(query='') {
    const value=await api(`/product/search?keyword=${encodeURIComponent(query)}`,{auth:false});
    if(!Array.isArray(value))throw new Error('Unexpected product list');
    state.products=value;
    renderHome();renderDeals();renderCatalog();
    if(isSeller())renderSeller();
    if(isAdmin())renderAdminProductSelect();
    return value;
  }
  function card(p) {
    const available=inStock(p),off=discountOf(p);
    return `<article class="product-card" data-product-id="${esc(p.id)}"><button class="product-media" type="button" data-open-product="${esc(p.id)}" aria-label="View ${esc(p.name)}" style="border:0;padding:0;width:100%;cursor:pointer">
      ${imageFor(p)}${off>0?`<span class="sale-tag">${off}% OFF</span>`:''}</button>
      <div class="product-card-body"><span class="product-brand">${esc(p.brand||labelFor(p.category))}</span><button type="button" data-open-product="${esc(p.id)}" class="product-name" style="border:0;background:none;padding:0;text-align:left;cursor:pointer">${esc(p.name)}</button>
      <div class="product-meta"><span class="rating">★ ${Number(p.averageRating||0).toFixed(1)} <small>(${Number(p.reviewCount||0)})</small></span><span class="stock-pill ${available?'':'out'}">${available?'In stock':'Unavailable'}</span></div>
      <div class="product-pricing"><strong>${money(p.price)}</strong>${off>0?`<del>${money(p.compareAtPrice)}</del><span class="discount">-${off}%</span>`:''}</div>
      <div class="product-actions"><button class="btn primary" type="button" data-add-cart="${esc(p.id)}" ${available?'':'disabled'}>＋ Add to cart</button><button class="btn outline" type="button" data-open-product="${esc(p.id)}" aria-label="View details">↗</button></div></div></article>`;
  }
  function renderHome(){
    const products=[...state.products].filter(p=>p.status==='AVAILABLE'&&inStock(p)).sort((a,b)=>discountOf(b)-discountOf(a) || Number(b.averageRating||0)-Number(a.averageRating||0) || String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
    el('featuredGrid').innerHTML=products.length?products.slice(0,5).map(card).join(''):empty('Your next favorite will appear here','There are no available products yet. Come back once sellers add listings.','<button class="btn outline" data-route="shop">View catalog</button>');
    // The spotlight promotes the largest *real* markdown, only if the backend returned compareAtPrice.
    const spotlight=el('dealSpotlight');
    const topDeal=products.find(p=>discountOf(p)>0);
    const kicker=spotlight.querySelector('.promo-kicker'),title=spotlight.querySelector('h2'),desc=spotlight.querySelector('p'),cta=spotlight.querySelector('button');
    if(topDeal){
      kicker.textContent='THE BEST VERIFIED SAVING';
      title.textContent=`Save ${discountOf(topDeal)}% on a great find.`;
      desc.textContent=topDeal.name;
      cta.removeAttribute('data-category');cta.dataset.openProduct=topDeal.id;cta.innerHTML='Explore this offer <span>↗</span>';
    } else {
      kicker.textContent='YOUR STYLE, REIMAGINED';title.innerHTML='Good things.<br>Better days.';
      desc.textContent='Explore little upgrades for everyday life.';
      cta.removeAttribute('data-open-product');cta.dataset.category='CLOTHING';cta.innerHTML='Explore style <span>↗</span>';
    }

  }
  function applyFilters() {
    let list=state.products.filter(p=>p.status!=='SUSPENDED');
    if(state.catalogQuery){const q=state.catalogQuery.toLowerCase().trim();list=list.filter(p=>[p.name,p.brand,p.description].some(x=>String(x||'').toLowerCase().includes(q)));}
    const category=el('filterCategory').value;
    if(category)list=list.filter(p=>p.category===category);
    if(el('filterInStock').checked)list=list.filter(inStock);
    const cap=Number(el('filterMaxPrice').value);if(el('filterMaxPrice').value.trim())list=list.filter(p=>Number(p.price)<=cap);
    const sort=el('filterSort').value;
    if(sort==='price-asc')list.sort((a,b)=>Number(a.price)-Number(b.price));
    if(sort==='price-desc')list.sort((a,b)=>Number(b.price)-Number(a.price));
    if(sort==='rating')list.sort((a,b)=>Number(b.averageRating||0)-Number(a.averageRating||0));
    if(sort==='name')list.sort((a,b)=>String(a.name).localeCompare(String(b.name)));
    if(sort==='newest')list.sort((a,b)=>String(b.createdAt||'').localeCompare(String(a.createdAt||'')));
    return list;
  }
  function pageButtons(container,total,page,onClickName){
    const pages=Math.max(1,Math.ceil(total/12));if(pages<=1){container.innerHTML='';return;}
    const items=[`<button class="page-button" data-${onClickName}="${page-1}" ${page===0?'disabled':''}>← Previous</button>`];
    for(let i=0;i<pages;i++){if(i>=page-2&&i<=page+2)items.push(`<button class="page-button ${i===page?'current':''}" data-${onClickName}="${i}">${i+1}</button>`);}
    items.push(`<button class="page-button" data-${onClickName}="${page+1}" ${page>=pages-1?'disabled':''}>Next →</button>`);container.innerHTML=items.join('');
  }
  function renderCatalog() {
    const list=applyFilters(),size=12;const page=Math.max(0,Math.min(state.catalogPage,Math.max(0,Math.ceil(list.length/size)-1)));state.catalogPage=page;
    el('catalogCount').textContent=`${list.length} products found`;
    el('catalogQueryLabel').textContent=state.catalogQuery?`Search: “${state.catalogQuery}”`:'';
    el('catalogGrid').innerHTML=list.length?list.slice(page*size,(page+1)*size).map(card).join(''):empty('No matching products','Try a different category, price, or search term.','<button class="btn outline" id="emptyClearFilters">Clear filters</button>');
    pageButtons(el('catalogPagination'),list.length,page,'catalog-page');
  }
  function renderDeals() {
    const available=state.products.filter(inStock);
    const discounted=available.filter(p=>discountOf(p)>0).sort((a,b)=>discountOf(b)-discountOf(a));
    const items=discounted.length?discounted:available.sort((a,b)=>Number(b.averageRating||0)-Number(a.averageRating||0)).slice(0,10);
    el('dealsDescription').textContent=discounted.length?'Shop products with actual seller-provided original prices and visible savings.':'Explore popular and highly rated products. Verified discounts will appear here when sellers provide original prices.';
    el('dealsGrid').innerHTML=items.length?items.map(card).join(''):empty('Special finds are coming','There are no available products in the store yet.');
  }
  async function showProduct(id) {
    try {
      // The detailed endpoint returns reviews, unlike the product search endpoint.
      const p=await api(`/products/${encodeURIComponent(id)}`,{auth:false}),off=discountOf(p),avail=inStock(p);
      el('productDetailBody').innerHTML=`<div class="product-detail"><div class="product-detail-image">${imageFor(p)}</div><div><span class="eyebrow">${esc(labelFor(p.category))} · ${esc(p.brand)}</span><h2>${esc(p.name)}</h2><div class="product-meta"><span class="rating">★ ${Number(p.averageRating||0).toFixed(1)} <small>(${Number(p.reviewCount||0)} reviews)</small></span>${status(p.status||'AVAILABLE')}</div><div class="product-pricing"><strong>${money(p.price)}</strong>${off>0?`<del>${money(p.compareAtPrice)}</del><span class="discount">Save ${off}%</span>`:''}</div><p>${esc(p.description||'')}</p><p>${avail?`${Number(p.stockQuantity)} available`:'Currently unavailable'}</p><button type="button" class="btn primary full" ${avail?'':'disabled'} data-add-cart="${esc(p.id)}">＋ Add to cart</button>${p.reviews?.length?`<div class="product-reviews"><h3>What customers are saying</h3>${p.reviews.slice(0,8).map(r=>`<div class="review-line"><strong>${esc(r.userFirstName||'Customer')} · ★ ${esc(r.rating)}</strong><div>${esc(r.comment||'')}</div></div>`).join('')}</div>`:''}</div></div>`;
      el('productDialog').showModal();
    } catch(e) {toast(e.message,'error');}
  }

  // Shopping cart and checkout — all writes go to the real backend.
  async function addToCart(id) {
    if(!requireAuth())return;
    if(!isBuyer()){toast('Shopping cart checkout is currently available to buyer accounts.','info');return;}
    try {state.cart=await api('/cart',{method:'POST',body:{productId:id,quantity:1}});updateIdentity();toast('Added to your cart ✦','success');if(state.currentRoute==='cart')renderCart();}
    catch(e){toast(e.message,'error');}
  }
  async function loadCart() {
    if(!isLoggedIn())return;
    el('cartItems').innerHTML=empty('Loading your cart…');
    try {state.cart=await api('/cart');updateIdentity();renderCart();}
    catch(e){el('cartItems').innerHTML=empty('Could not load your cart',e.message);el('cartSummary').innerHTML='';}
  }
  function cartItems() {return Array.isArray(state.cart?.items)?state.cart.items:[];}
  function renderCart() {
    const items=cartItems();
    el('cartItems').innerHTML=items.length?items.map(item=>`<article class="cart-item"><div class="order-item-picture" style="display:grid;place-items:center">${imageFor({productImageUrl:item.productImageUrl,name:item.productName})}</div><div class="cart-item-content"><h3>${esc(item.productName)}</h3><p>${money(item.price)} each</p><div class="cart-item-bottom"><div class="qty-controls"><button aria-label="Decrease quantity" data-cart-qty="${esc(item.productId)}" data-qty="${Math.max(0,Number(item.quantity)-1)}">−</button><span>${Number(item.quantity)}</span><button aria-label="Increase quantity" data-cart-qty="${esc(item.productId)}" data-qty="${Number(item.quantity)+1}">＋</button></div><strong>${money(item.subtotal)}</strong><button class="remove-link" data-remove-cart="${esc(item.productId)}">Remove</button></div></div></article>`).join(''):empty('Your cart looks a little empty','Ready for your next favorite? Discover something you love.','<button type="button" data-route="shop" class="btn primary">Discover products →</button>');
    const total=Number(state.cart?.total || 0);
    el('cartSummary').innerHTML=`<h2>Order summary</h2><div class="summary-line"><span>Items (${Number(state.cart?.totalItems||0)})</span><strong>${money(total)}</strong></div><div class="summary-line"><span>Delivery</span><span>At checkout</span></div><div class="summary-line total"><span>Subtotal</span><strong>${money(total)}</strong></div><button type="button" class="btn primary full" data-route="checkout" ${items.length?'':'disabled'}>Continue to checkout →</button><p>Delivery and applicable charges are confirmed during checkout.</p>`;
  }
  async function mutateCart(id,quantity,remove=false) {
    try {state.cart=await api(`/cart/${encodeURIComponent(id)}`,{method:remove?'DELETE':'PUT',...(remove?{}:{body:{productId:id,quantity}})});renderCart();updateIdentity();}
    catch(e){toast(e.message,'error');}
  }
  async function prepareCheckout() {
    if(!requireAuth())return;
    el('checkoutSummary').innerHTML='<p>Loading your items…</p>';
    try {
      state.cart=await api('/cart');updateIdentity();
      if(!cartItems().length){toast('Your cart is empty. Add a product first.','info');route('cart');return;}
      if(isBuyer())await fetchAddresses();
      const sel=el('checkoutSavedAddress');sel.innerHTML='<option value="">Enter a new address</option>'+(state.addresses||[]).map(a=>`<option value="${esc(a.id)}" ${a.default?'selected':''}>${esc(a.fullName)} — ${esc(a.city)}, ${esc(a.state)}</option>`).join('');
      if(sel.value)fillCheckoutAddress(sel.value);
      const total=Number(state.cart.total||0);
      el('checkoutSummary').innerHTML=`<h2>Your order</h2>${cartItems().map(i=>`<div class="summary-line"><span>${esc(i.productName)} × ${Number(i.quantity)}</span><strong>${money(i.subtotal)}</strong></div>`).join('')}<div class="summary-line"><span>Shipping</span><span>${money(0)} (current demo setting)</span></div><div class="summary-line total"><span>Estimated total</span><strong>${money(total)}</strong></div><p>Prices are finalized by the backend when you place your order. Cash on delivery does not process an online payment.</p>`;
    } catch(e){el('checkoutSummary').innerHTML=empty('Checkout unavailable',e.message);toast(e.message,'error');}
  }
  function fillCheckoutAddress(id) {
    const a=state.addresses.find(x=>x.id===id);if(!a)return;
    el('checkoutName').value=a.fullName||'';el('checkoutPhone').value=a.phone||'';
    el('checkoutStreet').value=[a.street,a.landmark].filter(Boolean).join(', ');
    el('checkoutCity').value=a.city||'';el('checkoutState').value=a.state||'';el('checkoutZip').value=a.zipCode||'';el('checkoutCountry').value='India';
  }
  async function placeOrder(event) {
    event.preventDefault();if(!requireAuth())return;
    const phone=el('checkoutPhone').value.trim().replace(/[\s-]/g,'');
    // Accepts 5-digit US ZIPs and 6-digit Indian PIN codes.
    const zip=el('checkoutZip').value.trim();
    if(!/^(?:\d{5}(?:-\d{4})?|\d{6})$/.test(zip)){toast('Enter a valid postal code (5 or 6 digits).','error');return;}
    const payload={paymentMethod:'CASH_ON_DELIVERY',deliveryNotes:el('checkoutNotes').value.trim(),shippingAddress:el('checkoutStreet').value.trim(),city:el('checkoutCity').value.trim(),state:el('checkoutState').value.trim(),zipCode:zip,country:el('checkoutCountry').value.trim(),phoneNumber:phone.length===10?'+91'+phone:phone,shippingCost:0};
    const btn=el('placeOrderBtn');btn.disabled=true;btn.textContent='Placing your order…';
    try {const order=await api('/orders',{method:'POST',body:payload});state.cart=null;updateIdentity();toast('Order placed! Time for a happy dance ✦','success');route('orders');await showOrder(order.id);}
    catch(e){toast(e.status===400?'Postal code validation failed. Use a 5-digit US ZIP or a 6-digit Indian PIN code.':e.message,'error');}
    finally {btn.disabled=false;btn.textContent='Place order →';}
  }

  // Orders, tracking, cancellations, returns, verified-purchase reviews.
  async function loadOrders(page=0) {
    state.ordersPage=Math.max(0,page);el('ordersList').innerHTML=empty('Loading your orders…');
    const f=el('orderStatusFilter').value;const path=f?`/orders/status/${encodeURIComponent(f)}`:'/orders';
    try {const data=await api(`${path}?page=${state.ordersPage}&size=10&sort=createdAt,desc`);
      const orders=Array.isArray(data.content)?data.content:[];
      el('ordersList').innerHTML=orders.length?orders.map(order=>`<article class="order-card"><div class="order-top"><div><strong>Order #${esc(String(order.id).slice(0,8).toUpperCase())}</strong><small>${esc(humanDate(order.createdAt))} · Tracking ${esc(order.trackingNumber||'Not assigned')}</small></div>${status(order.status)}</div><div class="order-items-line">${(order.items||[]).map(i=>`${esc(i.productName)} × ${Number(i.quantity)}`).join(' · ')}</div><div class="order-bottom"><strong>${money(order.orderTotal||order.totalAmount)}</strong><div class="button-row"><button type="button" class="btn outline btn-sm" data-view-order="${esc(order.id)}">View & track ↗</button>${['PENDING','CONFIRMED','PROCESSING'].includes(order.status)?`<button type="button" class="btn ghost btn-sm" data-cancel-order="${esc(order.id)}">Cancel order</button>`:''}</div></div></article>`).join(''):empty('No orders here yet','Your next great find is waiting for you.','<button class="btn primary" data-route="shop">Explore products →</button>');
      const pagination=el('ordersPagination');pagination.innerHTML='';
      if(data.totalPages>1) pagination.innerHTML=`<button class="page-button" data-order-page="${state.ordersPage-1}" ${data.first?'disabled':''}>← Previous</button><span class="muted">Page ${state.ordersPage+1} / ${data.totalPages}</span><button class="page-button" data-order-page="${state.ordersPage+1}" ${data.last?'disabled':''}>Next →</button>`;
    }catch(e){el('ordersList').innerHTML=empty('Could not load orders',e.message);}
  }
  async function showOrder(id) {
    try {
      const [order,tracking]=await Promise.all([api(`/orders/${encodeURIComponent(id)}`),api(`/orders/${encodeURIComponent(id)}/track`).catch(()=>null)]);
      state.currentOrder=order;
      const canCancel=['PENDING','CONFIRMED','PROCESSING'].includes(order.status),canReturn=order.status==='DELIVERED';
      el('orderDetailBody').innerHTML=`<span class="eyebrow">YOUR ORDER</span><h2>Order ${esc(String(order.id).slice(0,8).toUpperCase())}</h2><div class="order-detail-row"><span>Status</span>${status(order.status)}</div><div class="order-detail-row"><span>Tracking number</span><strong>${esc(order.trackingNumber||'Pending')}</strong></div><div class="order-detail-row"><span>Placed on</span><strong>${esc(humanDate(order.createdAt))}</strong></div>${tracking?.carrier?`<div class="order-detail-row"><span>Carrier</span><strong>${esc(tracking.carrier)}</strong></div>`:''}<div class="order-detail-row"><span>Delivery address</span><strong>${esc([order.shippingAddress,order.city,order.state,order.zipCode].filter(Boolean).join(', '))}</strong></div><h3>Items in this order</h3>${(order.items||[]).map(item=>`<div class="order-item-detail"><div><strong>${esc(item.productName)}</strong><div class="muted">Qty ${Number(item.quantity)} · ${money(item.subtotal)}</div></div>${canReturn?`<button class="btn outline btn-sm" data-review-item="${esc(item.id)}">Write review</button>`:''}</div>`).join('')}<div class="order-detail-row"><span>Order total</span><strong>${money(order.orderTotal)}</strong></div><div class="form-actions">${canCancel?`<button class="btn danger-outline" data-cancel-order="${esc(order.id)}">Cancel order</button>`:''}${canReturn?`<button class="btn outline" data-return-order="${esc(order.id)}">Request a return</button>`:''}</div><div id="orderInlineForm"></div>`;
      if(!el('orderDialog').open)el('orderDialog').showModal();
    }catch(e){toast(e.message,'error');}
  }
  async function cancelOrder(id){if(!window.confirm('Cancel this order?'))return;try{await api(`/orders/${encodeURIComponent(id)}/cancel`,{method:'PUT'});toast('Order cancelled','success');closeDialog('orderDialog');await loadOrders(state.ordersPage);}catch(e){toast(e.message,'error');}}
  function showReturnForm(id) {el('orderInlineForm').innerHTML=`<form id="returnForm" class="review-form" data-order-id="${esc(id)}"><h3>Request a return</h3><select id="returnReason" required><option value="">Choose a reason</option>${['DEFECTIVE_PRODUCT','WRONG_ITEM','SIZE_ISSUE','COLOR_ISSUE','DAMAGED_IN_TRANSIT','NOT_AS_DESCRIBED','LATE_DELIVERY','NO_LONGER_NEEDED','OTHER'].map(r=>`<option value="${r}">${esc(labelFor(r))}</option>`).join('')}</select><textarea id="returnComment" maxlength="1000" rows="3" placeholder="Tell us more (optional)"></textarea><button class="btn primary" type="submit">Submit return request</button></form>`;}
  function showReviewForm(itemId) {el('orderInlineForm').innerHTML=`<form id="reviewForm" class="review-form" data-item-id="${esc(itemId)}"><h3>Share your review</h3><select id="reviewRating" required><option value="5">★★★★★ — 5 stars</option><option value="4">★★★★ — 4 stars</option><option value="3">★★★ — 3 stars</option><option value="2">★★ — 2 stars</option><option value="1">★ — 1 star</option></select><textarea id="reviewComment" maxlength="1000" rows="3" required placeholder="How was your experience?"></textarea><button class="btn primary" type="submit">Post review</button></form>`;}

  // User verification, seller application, and saved addresses.
  async function fetchAddresses(){if(!isBuyer())return [];try{const v=await api('/user/addresses');state.addresses=Array.isArray(v)?v:[];return state.addresses;}catch(e){if(e.status!==403)toast(e.message,'error');return [];} }
  async function loadAccount(){if(!isLoggedIn())return;updateIdentity();await Promise.all([refreshEmailStatus(),loadProfile(),isBuyer()?fetchAddresses():Promise.resolve(),isBuyer()||isSeller()?fetchSellerApplication():Promise.resolve()]);renderAddresses();}
  function renderAddresses(){if(!isBuyer())return;
    el('addressesList').innerHTML=state.addresses.length?state.addresses.map(a=>`<div class="address-card"><div><strong>${esc(a.fullName)} ${a.default?'<span class="stock-pill">Default</span>':''}</strong><p>${esc([a.street,a.landmark,a.city,a.state,a.zipCode].filter(Boolean).join(', '))}</p><p>${esc(a.phone||'')}</p></div><div class="button-row"><button class="btn ghost btn-sm" data-edit-address="${esc(a.id)}">Edit</button><button class="btn ghost btn-sm" data-delete-address="${esc(a.id)}">✕</button></div></div>`).join(''):empty('No saved addresses','Add an address to make checkout easier.');
  }
  function addressForm(show,addr=null){el('addressForm').classList.toggle('hidden',!show);if(!show)return;const a=addr||{};el('addressFormTitle').textContent=addr?'Edit address':'Add new address';el('addressId').value=a.id||'';for(const [input,key] of [['addressName','fullName'],['addressPhone','phone'],['addressStreet','street'],['addressCity','city'],['addressState','state'],['addressZip','zipCode'],['addressLandmark','landmark']])el(input).value=a[key]||'';el('addressDefault').checked=Boolean(a.default);el('addressForm').scrollIntoView({block:'nearest',behavior:'smooth'});}
  async function saveAddress(event){event.preventDefault();const id=el('addressId').value;const body={fullName:el('addressName').value.trim(),phone:el('addressPhone').value.trim(),street:el('addressStreet').value.trim(),city:el('addressCity').value.trim(),state:el('addressState').value.trim(),zipCode:el('addressZip').value.trim(),landmark:el('addressLandmark').value.trim(),isDefault:el('addressDefault').checked};try{await api(id?`/user/addresses/${encodeURIComponent(id)}`:'/user/addresses',{method:id?'PUT':'POST',body});toast('Address saved','success');addressForm(false);await fetchAddresses();renderAddresses();}catch(e){toast(e.message,'error');}}
  async function deleteAddress(id){if(!window.confirm('Remove this saved address?'))return;try{await api(`/user/addresses/${encodeURIComponent(id)}`,{method:'DELETE'});toast('Address removed','success');await fetchAddresses();renderAddresses();}catch(e){toast(e.message,'error');}}
  async function fetchSellerApplication(){
    try{state.seller=await api('/seller/me');if(state.seller?.status==='APPROVED')state.role='SELLER';}
    catch(e){if(e.status===404)state.seller=null;else if(e.status!==403)console.warn('Seller application lookup',e.message);}
    updateIdentity();
    const app=state.seller,box=el('sellerApplicationStatus');
    if(app){box.innerHTML=`<div class="order-detail-row"><span>${esc(app.storeName||'Your store')}</span>${status(app.status)}</div><p class="muted">${app.status==='PENDING'?'Your application is with the admin team. Seller tools will be available when approved.':app.status==='APPROVED'?'Your store is approved and ready to go.':'This seller application is currently suspended.'}</p>`;}
    else box.textContent='Have a shop in mind? Apply to join our sellers.';
    el('sellerApplicationForm').classList.toggle('hidden',Boolean(app));
    return app;
  }
  async function submitSellerApplication(event){event.preventDefault();if(!isBuyer())return;try{const app=await api('/seller/become',{method:'POST',body:{storeName:el('applyStoreName').value.trim(),storeLogoUrl:el('applyStoreLogo').value.trim(),businessAddress:el('applyBusinessAddress').value.trim()}});state.seller=app;toast('Application sent! An admin will review your store.','success');await fetchSellerApplication();}catch(e){toast(e.message,'error');}}

  // User profile management
  async function loadProfile() {
    if (!isLoggedIn()) return;
    try {
      state.profile = await api('/user/me');
      const p = state.profile;
      el('pfFirstName').value = p.firstName || '';
      el('pfLastName').value = p.lastName || '';
      el('pfPhone').value = p.phone || '';
      el('pfGender').value = p.gender || '';
      el('pfAge').value = p.age ?? '';
      // Update sidebar info with full name
      if (p.firstName) {
        el('profileName').textContent = `${p.firstName} ${p.lastName || ''}`.trim();
        el('profileAvatar').textContent = (p.firstName[0] || 'S').toUpperCase();
      }
    } catch(e) {
      if (e.status !== 404 && e.status !== 401) console.warn('Could not load profile', e.message);
    }
  }

  async function saveProfile(event) {
    event.preventDefault();
    if (!isLoggedIn()) return;
    const body = {
      firstName: el('pfFirstName').value.trim(),
      lastName: el('pfLastName').value.trim(),
      phone: el('pfPhone').value.trim() || null,
      gender: el('pfGender').value || null,
      age: el('pfAge').value ? Number(el('pfAge').value) : null
    };
    const btn = el('saveProfileBtn');
    btn.disabled = true;
    try {
      const updated = await api('/user/me', { method: 'PUT', body });
      state.profile = updated;
      toast('Profile updated successfully!', 'success');
      // Refresh display
      if (updated.firstName) {
        el('profileName').textContent = `${updated.firstName} ${updated.lastName || ''}`.trim();
        el('profileAvatar').textContent = (updated.firstName[0] || 'S').toUpperCase();
      }
    } catch(e) {
      toast(e.message, 'error');
    } finally {
      btn.disabled = false;
    }
  }

  // Seller inventory and admin moderation.
  function sellerProducts(){return isSeller()?state.products.filter(p=>String(p.sellerId)===String(state.seller.id)):[];}
  function renderSeller(){if(!isSeller())return;const list=sellerProducts();el('sellerListingCount').textContent=list.length;el('sellerStockCount').textContent=list.filter(inStock).length;el('sellerStoreName').textContent=state.seller.storeName||'My store';el('inventoryCount').textContent=`${list.length} listings`;el('sellerInventory').innerHTML=list.length?list.map(p=>`<div class="inventory-row"><div class="order-item-picture" style="display:grid;place-items:center">${imageFor(p)}</div><div><strong>${esc(p.name)}</strong><small>${money(p.price)} · ${Number(p.stockQuantity)} in stock</small><br>${status(p.status)}</div><div class="button-row"><button type="button" class="btn outline btn-sm" data-edit-product="${esc(p.id)}">Edit</button><button type="button" class="btn ghost btn-sm" data-delete-product="${esc(p.id)}">Delete</button></div></div>`).join(''):empty('Your store is ready','Add your first product to share it with shoppers.');}
  function resetProductForm(){el('productForm').reset();el('editingProductId').value='';el('productFormTitle').textContent='Add a product';el('saveProductBtn').textContent='Publish product';}
  function editProduct(id){const p=state.products.find(x=>x.id===id);if(!p||!isSeller()||String(p.sellerId)!==String(state.seller.id))return;el('editingProductId').value=id;el('pName').value=p.name||'';el('pBrand').value=p.brand||'';el('pCategory').value=p.category||'';el('pPrice').value=p.price;el('pCompareAtPrice').value=p.compareAtPrice||'';el('pStock').value=p.stockQuantity;el('pImage').value=p.imageUrl||'';el('pDescription').value=p.description||'';el('productFormTitle').textContent='Edit your product';el('saveProductBtn').textContent='Save changes';el('productForm').scrollIntoView({behavior:'smooth'});}
  async function saveProduct(event){event.preventDefault();if(!isSeller())return;const id=el('editingProductId').value;const body={name:el('pName').value.trim(),brand:el('pBrand').value.trim(),category:el('pCategory').value,price:Number(el('pPrice').value),stockQuantity:Number(el('pStock').value),imageUrl:el('pImage').value.trim(),description:el('pDescription').value.trim()};if(el('pCompareAtPrice').value.trim()){body.compareAtPrice=Number(el('pCompareAtPrice').value);if(body.compareAtPrice<=body.price){toast('Original price must be higher than the selling price.','error');return;}}try{await api(id?`/product/${encodeURIComponent(id)}`:'/product',{method:id?'PUT':'POST',body});toast(id?'Product updated':'Product published','success');resetProductForm();await loadProducts('');renderSeller();}catch(e){toast(e.message,'error');}}
  async function deleteProduct(id){if(!isSeller()||!window.confirm('Remove this product from your store?'))return;try{await api(`/product/${encodeURIComponent(id)}`,{method:'DELETE'});toast('Product deleted','success');await loadProducts('');renderSeller();}catch(e){toast(e.message,'error');}}
  function renderAdminProductSelect(){el('adminProductsCount').textContent=state.products.length;el('suspendProductId').innerHTML='<option value="">Choose a product</option>'+state.products.filter(p=>p.status!=='SUSPENDED').map(p=>`<option value="${esc(p.id)}">${esc(p.name)} · ${esc(p.brand)}</option>`).join('');}
  async function loadAdmin(){if(!isAdmin())return;renderAdminProductSelect();await Promise.all([loadPendingSellers(),loadAdminUsers()]);}
  async function loadPendingSellers(){try{const list=await api('/admin/sellers/pending');el('adminPendingCount').textContent=list.length;el('pendingSellers').innerHTML=list.length?list.map(s=>`<div class="pending-row"><div><strong>${esc(s.storeName)}</strong><small>${esc(s.businessAddress||'Business address not provided')} · ${esc(s.id)}</small></div><div class="button-row"><button type="button" class="btn primary btn-sm" data-approve-seller="${esc(s.id)}">Approve →</button><button type="button" class="btn ghost btn-sm" data-fill-seller="${esc(s.id)}">Suspend</button></div></div>`).join(''):empty('All caught up','No seller applications are waiting for approval.');}catch(e){el('pendingSellers').innerHTML=empty('Could not load applications',e.message);}}
  async function approveSeller(id){if(!isAdmin())return;if(!window.confirm('Approve this seller application?'))return;try{await api(`/admin/seller/${encodeURIComponent(id)}/approve`,{method:'PUT'});toast('Seller approved','success');await loadPendingSellers();}catch(e){toast(e.message,'error');}}
  async function loadAdminUsers(page=null){if(!isAdmin())return;if(page!==null)el('usersPage').value=String(Math.max(0,page));const params=new URLSearchParams({page:el('usersPage').value||'0',size:'10',sort:'createdAt,desc'});if(el('usersRole').value)params.set('role',el('usersRole').value);try{const data=await api(`/admin/users?${params}`);el('adminUsersCount').textContent=String(data.content?.length||0);el('usersList').innerHTML=data.content?.length?data.content.map(u=>`<div class="user-row"><div><strong>${esc([u.firstName,u.lastName].filter(Boolean).join(' ')||u.email)}</strong><small>${esc(u.email)}</small><small>${esc(u.id)}</small></div><div>${status(u.suspended?'SUSPENDED':u.role)}</div></div>`).join(''):empty('No accounts matched','Try a different role filter.');el('usersPagination').innerHTML=`<button type="button" class="page-button" data-users-page="${Number(data.number||0)-1}" ${data.first?'disabled':''}>← Previous</button><span class="muted">Page ${Number(data.number||0)+1} of ${data.totalPages||1}</span><button type="button" class="page-button" data-users-page="${Number(data.number||0)+1}" ${data.last?'disabled':''}>Next →</button>`;}catch(e){el('usersList').innerHTML=empty('Could not load accounts',e.message);}}

  const banners=[
    {eyebrow:'THE EVERYDAY EDIT',title:'Find something<br>you\'ll <em>love.</em>',description:'Beautiful finds, useful things, and that just-right pick. Your next favorite starts here.',cta:'Explore the collection',category:null},
    {eyebrow:'FIND YOUR NEXT FAVORITE',title:'New finds.<br>Fresh <em>energy.</em>',description:'Go from browsing to discovering. Explore newly added products from across the store.',cta:'See the latest',category:null},
    {eyebrow:'CURATED FOR THE CURIOUS',title:'A little more<br>to <em>discover.</em>',description:'From everyday essentials to unexpected delights, good things are just a click away.',cta:'Discover more',category:null}
  ];
  function setBanner(index){state.bannerIndex=(index+3)%3;const b=banners[state.bannerIndex];el('homeHero').dataset.slide=String(state.bannerIndex);el('homeHeading').innerHTML=b.title;el('homeHero').querySelector('.hero-eyebrow').lastChild.textContent=` ${b.eyebrow}`;el('homeHero').querySelector('.hero-content p').textContent=b.description;el('homeHero').querySelector('.hero-cta').firstChild.textContent=b.cta+' ';document.querySelectorAll('.hero-dot').forEach(dot=>dot.classList.toggle('is-current',Number(dot.dataset.slide)===state.bannerIndex));}
  function renderSuggestions(value){const q=value.trim().toLowerCase();if(!q){el('searchSuggestions').classList.add('hidden');return;}const items=state.products.filter(p=>p.status!=='SUSPENDED'&&[p.name,p.brand,p.description].some(s=>String(s||'').toLowerCase().includes(q))).slice(0,5);if(!items.length){el('searchSuggestions').classList.add('hidden');return;}el('searchSuggestions').innerHTML=items.map(p=>`<button type="button" class="suggestion-item" data-open-product="${esc(p.id)}">${safeImage(p.imageUrl)?`<img src="${safeImage(p.imageUrl)}" alt="">`:`<span>${categoryIcon(p.category)}</span>`}<span><strong>${esc(p.name)}</strong><small>${money(p.price)} · ${esc(p.brand)}</small></span></button>`).join('');el('searchSuggestions').classList.remove('hidden');}

  function bindEvents(){
    document.body.addEventListener('click',async e=>{
      const button=e.target.closest('button');if(!button)return;
      if(button.hasAttribute('data-route')){route(button.dataset.route);return;}
      if(button.hasAttribute('data-category')){selectCategory(button.dataset.category);return;}
      if(button.hasAttribute('data-slide')){setBanner(Number(button.dataset.slide));return;}
      if(button.hasAttribute('data-open-product')){closeCategoryDropdown();el('searchSuggestions').classList.add('hidden');await showProduct(button.dataset.openProduct);return;}
      if(button.hasAttribute('data-add-cart')){await addToCart(button.dataset.addCart);return;}
      if(button.hasAttribute('data-cart-qty')){await mutateCart(button.dataset.cartQty,Number(button.dataset.qty));return;}
      if(button.hasAttribute('data-remove-cart')){await mutateCart(button.dataset.removeCart,0,true);return;}
      if(button.hasAttribute('data-catalog-page')){state.catalogPage=Number(button.dataset.catalogPage);renderCatalog();el('shopHeading').scrollIntoView({behavior:'smooth'});return;}
      if(button.hasAttribute('data-order-page')){await loadOrders(Number(button.dataset.orderPage));return;}
      if(button.hasAttribute('data-view-order')){await showOrder(button.dataset.viewOrder);return;}
      if(button.hasAttribute('data-cancel-order')){await cancelOrder(button.dataset.cancelOrder);return;}
      if(button.hasAttribute('data-return-order')){showReturnForm(button.dataset.returnOrder);return;}
      if(button.hasAttribute('data-review-item')){showReviewForm(button.dataset.reviewItem);return;}
      if(button.hasAttribute('data-edit-address')){addressForm(true,state.addresses.find(a=>a.id===button.dataset.editAddress));return;}
      if(button.hasAttribute('data-delete-address')){await deleteAddress(button.dataset.deleteAddress);return;}
      if(button.hasAttribute('data-edit-product')){editProduct(button.dataset.editProduct);return;}
      if(button.hasAttribute('data-delete-product')){await deleteProduct(button.dataset.deleteProduct);return;}
      if(button.hasAttribute('data-approve-seller')){await approveSeller(button.dataset.approveSeller);return;}
      if(button.hasAttribute('data-fill-seller')){el('suspendSellerId').value=button.dataset.fillSeller;el('suspendSellerId').scrollIntoView({behavior:'smooth'});return;}
      if(button.hasAttribute('data-users-page')){await loadAdminUsers(Number(button.dataset.usersPage));return;}
      if(button.hasAttribute('data-auth-tab')){setAuthTab(button.dataset.authTab);return;}
      if(button.hasAttribute('data-close-dialog')){closeDialog(button.dataset.closeDialog);return;}
      if(button.hasAttribute('data-guest')){closeDialog('authDialog');route('home');return;}
      if(button.id==='emptyClearFilters'){resetFilters();return;}
    });
    el('categoryToggle').addEventListener('click',()=>{const hide=el('categoryDropdown').classList.toggle('hidden');el('categoryToggle').setAttribute('aria-expanded',String(!hide));});
    document.addEventListener('click',e=>{if(!e.target.closest('#categoryMenu'))closeCategoryDropdown();if(!e.target.closest('#headerSearch'))el('searchSuggestions').classList.add('hidden');});
    el('accountBtn').addEventListener('click',()=>isLoggedIn()?route('account'):openAuth());
    el('closeAuthBtn').addEventListener('click',()=>closeDialog('authDialog'));
    el('forgotPasswordBtn').addEventListener('click',()=>{el('resetEmail').value=el('loginEmail').value.trim();setAuthTab('reset');});
    el('globalSearch').addEventListener('input',e=>renderSuggestions(e.target.value));
    el('headerSearch').addEventListener('submit',async e=>{e.preventDefault();const q=el('globalSearch').value.trim();state.catalogQuery=q;state.catalogPage=0;state.category='';el('filterCategory').value='';try{await loadProducts(q);}catch(err){toast(err.message,'error');}route('shop');el('searchSuggestions').classList.add('hidden');});
    for(const id of ['filterCategory','filterSort','filterInStock','filterMaxPrice'])el(id).addEventListener(id==='filterMaxPrice'?'input':'change',()=>{state.catalogPage=0;renderCatalog();});
    el('resetFilters').addEventListener('click',resetFilters);
    el('loginForm').addEventListener('submit',login);
    el('registerForm').addEventListener('submit',register);
    el('requestResetBtn').addEventListener('click',requestResetOtp);
    el('resetForm').addEventListener('submit',resetPassword);
    el('logoutBtn').addEventListener('click',()=>{setSession('');route('home');openAuth();toast('You have signed out. See you soon!','info');});
    el('sendEmailOtpBtn').addEventListener('click',sendVerificationOtp);
    el('verifyEmailOtpBtn').addEventListener('click',verifyEmailOtp);
    el('resetEmail').addEventListener('input',updateOtpButtons);
    el('newAddressBtn').addEventListener('click',()=>addressForm(true));
    el('cancelAddressBtn').addEventListener('click',()=>addressForm(false));
    el('addressForm').addEventListener('submit',saveAddress);
    el('sellerApplicationForm').addEventListener('submit',submitSellerApplication);
    el('profileForm').addEventListener('submit',saveProfile);
    el('checkoutSavedAddress').addEventListener('change',e=>{if(e.target.value)fillCheckoutAddress(e.target.value);});
    el('checkoutForm').addEventListener('submit',placeOrder);
    el('orderStatusFilter').addEventListener('change',()=>loadOrders(0));
    el('productForm').addEventListener('submit',saveProduct);
    el('resetProductBtn').addEventListener('click',resetProductForm);
    el('sellerRefresh').addEventListener('click',async()=>{try{await fetchSellerApplication();await loadProducts('');toast('Listings refreshed','success');}catch(err){toast(err.message,'error');}});
    el('adminRefreshBtn').addEventListener('click',loadAdmin);
    el('userFilters').addEventListener('submit',e=>{e.preventDefault();loadAdminUsers();});
    el('suspendProductForm').addEventListener('submit',async e=>{e.preventDefault();const id=el('suspendProductId').value;try{await api(`/admin/product/${encodeURIComponent(id)}/suspend`,{method:'PUT',body:{reason:el('suspendProductReason').value.trim()}});toast('Product suspended','success');el('suspendProductForm').reset();await loadProducts('');}catch(err){toast(err.message,'error');}});
    el('suspendSellerForm').addEventListener('submit',async e=>{e.preventDefault();const id=el('suspendSellerId').value.trim();try{await api(`/admin/seller/${encodeURIComponent(id)}/suspend`,{method:'PUT',body:{reason:el('suspendSellerReason').value.trim()}});toast('Seller suspended','success');el('suspendSellerForm').reset();await loadPendingSellers();}catch(err){toast(err.message,'error');}});
    el('orderDialog').addEventListener('submit',async e=>{if(e.target.id==='returnForm'){e.preventDefault();const id=e.target.dataset.orderId;try{await api(`/orders/${encodeURIComponent(id)}/return`,{method:'PUT',body:{returnReason:el('returnReason').value,comment:el('returnComment').value.trim()}});toast('Return request submitted','success');closeDialog('orderDialog');await loadOrders(state.ordersPage);}catch(err){toast(err.message,'error');}}
      if(e.target.id==='reviewForm'){e.preventDefault();const id=e.target.dataset.itemId;try{await api(`/orders/items/${encodeURIComponent(id)}/review`,{method:'POST',body:{rating:Number(el('reviewRating').value),comment:el('reviewComment').value.trim()}});toast('Thanks for sharing your review!','success');closeDialog('orderDialog');await loadOrders(state.ordersPage);}catch(err){toast(err.message,'error');}}});
    for(const id of ['productDialog','orderDialog'])el(id).addEventListener('click',e=>{if(e.target===el(id))closeDialog(id);});
    document.addEventListener('error',e=>{if(e.target?.tagName==='IMG'){e.target.style.display='none';if(e.target.parentElement&&!e.target.parentElement.querySelector('.media-fallback')){const fallback=document.createElement('span');fallback.className='media-fallback';fallback.textContent='✳';e.target.parentElement.appendChild(fallback);}}},true);
    window.addEventListener('hashchange',renderRoute);
    document.addEventListener('visibilitychange',()=>{if(document.hidden)el('searchSuggestions').classList.add('hidden');});
  }
  function resetFilters(){state.catalogQuery='';state.catalogPage=0;state.category='';el('filterCategory').value='';el('filterSort').value='newest';el('filterMaxPrice').value='';el('filterInStock').checked=false;el('globalSearch').value='';loadProducts('').catch(err=>toast(err.message,'error'));renderCatalog();}
  async function login(e){e.preventDefault();clearLoginError();const button=e.target.querySelector('button[type=submit]');button.disabled=true;try{const result=await api('/auth/login',{method:'POST',auth:false,body:{email:el('loginEmail').value.trim(),password:el('loginPassword').value}});const token=typeof result==='string'?result.trim():result?.token||result?.accessToken;if(!/^[\w-]+\.[\w-]+\.[\w-]+$/.test(token||''))throw new Error('Login failed: the server did not return a valid token.');setSession(token);await hydrateSession();closeDialog('authDialog');toast('Welcome to ShopSphere ✦','success');route('home');try{await loadProducts('');}catch{ /* search errors are shown separately */ }}catch(err){const message=[401,403].includes(err.status)?'Incorrect email or password. Please try again.':err.message;showLoginError(message);}finally{button.disabled=false;}}
  async function register(e){e.preventDefault();const body={firstName:el('regFirst').value.trim(),lastName:el('regLast').value.trim(),email:el('regEmail').value.trim(),phone:el('regPhone').value.trim()||null,password:el('regPassword').value,gender:el('regGender').value||null,age:el('regAge').value?Number(el('regAge').value):null};try{await api('/user/register',{method:'POST',auth:false,body});toast('Your account is ready. Sign in to get started!','success');el('loginEmail').value=body.email;setAuthTab('login');}catch(err){toast(err.message,'error');}}
  async function requestResetOtp(){
    const email=el('resetEmail').value.trim();
    if(!email || !el('resetEmail').checkValidity()){toast('Enter a valid email address first.','info');return;}
    if(state.resetOtpSending || otpSecondsRemaining('reset',email)>0)return;
    state.resetOtpSending=true;
    updateOtpButtons();
    try{
      await api(`/auth/forgot-password?email=${encodeURIComponent(email)}`,{method:'POST',auth:false});
      startOtpCooldown('reset',email);
      toast('If the email exists, a recovery code has been sent.','success');
    }catch(err){
      if(err.status===429)startOtpCooldown('reset',email);
      toast(err.message,'error');
    }finally{state.resetOtpSending=false;updateOtpButtons();}
  }
  async function resetPassword(e){e.preventDefault();try{await api('/auth/reset-password',{method:'POST',auth:false,body:{email:el('resetEmail').value.trim(),otp:el('resetOtp').value.trim(),newPassword:el('resetNewPassword').value}});toast('Password updated. Sign in with your new password.','success');el('loginEmail').value=el('resetEmail').value;setAuthTab('login');}catch(err){toast(err.message,'error');}}

  async function init(){renderCategories();bindEvents();setBanner(0);updateIdentity();renderEmailVerification();updateOtpButtons();renderRoute();
    try{await loadProducts('');}catch(e){el('featuredGrid').innerHTML=empty('The store is temporarily unavailable',e.message);el('catalogGrid').innerHTML=empty('Could not load the catalog',e.message);el('dealsGrid').innerHTML=empty('Could not load special finds',e.message);}
    await hydrateSession();renderRoute();
    if(!isLoggedIn())openAuth();
    window.setInterval(()=>{if(!document.hidden&&state.currentRoute==='home')setBanner(state.bannerIndex+1);},7000);
    window.setInterval(updateOtpButtons,1000);
  }
  document.addEventListener('DOMContentLoaded',init);
})();
