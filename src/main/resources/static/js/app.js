(() => {
  'use strict';

  // ==================== CONSTANTS & ENUMS ====================
  const CATEGORIES = [
    'ELECTRONICS',
    'CLOTHING',
    'HOME_AND_KITCHEN',
    'BOOKS',
    'BEAUTY_AND_PERSONAL_CARE',
    'SPORTS_AND_OUTDOORS',
    'TOYS_AND_GAMES',
    'AUTOMOTIVE',
    'HEALTH_AND_WELLNESS',
    'FOOD_AND_BEVERAGES',
    'JEWELRY_AND_ACCESSORIES',
    'PET_SUPPLIES',
    'OFFICE_PRODUCTS',
    'MUSICAL_INSTRUMENTS',
    'ART_AND_CRAFT',
    'BABY_PRODUCTS',
    'GROCERY',
    'OTHER'
  ];

  const RETURN_REASONS = [
    'DEFECTIVE_PRODUCT',
    'WRONG_ITEM',
    'SIZE_ISSUE',
    'COLOR_ISSUE',
    'DAMAGED_IN_TRANSIT',
    'NOT_AS_DESCRIBED',
    'LATE_DELIVERY',
    'NO_LONGER_NEEDED',
    'OTHER'
  ];

  const FALLBACK_IMG = 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&auto=format&fit=crop&q=80';

  // ==================== STATE ====================
  const state = {
    token: localStorage.getItem('ecommerce_jwt') || null,
    user: null, // { username, authorities: [] }
    products: [],
    cart: null,
    addresses: [],
    orders: []
  };

  // ==================== DOM HELPERS ====================
  const $ = (id) => document.getElementById(id);

  function showToast(message, type = 'info') {
    const container = $('toastContainer');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.remove();
    }, 4200);
  }

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatCurrency(amount) {
    const num = Number(amount || 0);
    return '$' + num.toFixed(2);
  }

  function formatDate(isoStr) {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    if (Number.isNaN(d.getTime())) return escapeHtml(isoStr);
    return d.toLocaleString();
  }

  // ==================== API FETCH WRAPPER ====================
  async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) {
      headers.set('Authorization', `Bearer ${state.token}`);
    }
    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
      options.body = JSON.stringify(options.body);
    }

    const response = await fetch(path, {
      ...options,
      headers
    });

    const rawText = await response.text();
    let data = rawText;
    if (rawText) {
      try {
        data = JSON.parse(rawText);
      } catch {
        data = rawText;
      }
    } else {
      data = null;
    }

    if (!response.ok) {
      let errMsg = `HTTP ${response.status}`;
      if (data && typeof data === 'object') {
        errMsg = data.message || data.error || JSON.stringify(data);
      } else if (typeof data === 'string' && data.trim()) {
        errMsg = data;
      }
      if (response.status === 401 || response.status === 403) {
        if (!state.token) {
          errMsg = 'Please sign in first to perform this action.';
        } else if (errMsg === `HTTP ${response.status}`) {
          errMsg = response.status === 403
            ? 'Access forbidden (403): Your current role does not have permission for this endpoint.'
            : 'Session expired or unauthorized (401). Please sign in again.';
        }
      }
      throw new Error(errMsg);
    }

    return data;
  }

  // ==================== TAB NAVIGATION ====================
  function switchTab(tabName) {
    document.querySelectorAll('.nav-tab[data-tab]').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    document.querySelectorAll('.tab-panel').forEach((panel) => {
      panel.classList.toggle('active', panel.id === `tab-${tabName}`);
    });

    if (tabName === 'shop') {
      loadProducts($('globalSearchInput').value.trim());
    } else if (tabName === 'cart' && state.token) {
      loadCart();
    } else if (tabName === 'orders' && state.token) {
      loadOrders();
    } else if (tabName === 'account' && state.token) {
      fetchSessionDebug();
      loadAddresses();
    } else if (tabName === 'seller') {
      renderSellerProductsTable();
    }
  }

  // ==================== SESSION & AUTH ====================
  function setToken(token) {
    state.token = token;
    if (token) {
      localStorage.setItem('ecommerce_jwt', token);
    } else {
      localStorage.removeItem('ecommerce_jwt');
      state.user = null;
    }
    updateAuthUI();
  }

  function updateAuthUI() {
    const isLoggedIn = Boolean(state.token);
    $('openAuthModalBtn').classList.toggle('hidden', isLoggedIn);
    $('logoutBtn').classList.toggle('hidden', !isLoggedIn);
    $('userBadge').classList.toggle('hidden', !isLoggedIn);

    if (isLoggedIn && state.user) {
      $('userEmailDisplay').textContent = state.user.username || 'Signed In';
      const roleStr = (state.user.authorities || [])
        .map((r) => r.replace(/^ROLE_/, ''))
        .join(', ') || 'USER';
      $('userRoleBadge').textContent = roleStr;

      $('sessionDebugBox').innerHTML = `
        <div class="summary-line"><span>Authenticated Email:</span> <strong>${escapeHtml(state.user.username)}</strong></div>
        <div class="summary-line"><span>Granted Authorities:</span> <span class="badge badge-role">${escapeHtml((state.user.authorities || []).join(', '))}</span></div>
        <div class="summary-line"><span>JWT Token Status:</span> <span class="badge badge-success">Active</span></div>
      `;
    } else if (!isLoggedIn) {
      $('cartCountBadge').textContent = '0';
      $('sessionDebugBox').innerHTML = `<p class="text-muted">Not signed in. Click "Sign In / Register" in the top bar.</p>`;
    }
  }

  async function fetchSessionDebug() {
    if (!state.token) {
      updateAuthUI();
      return;
    }
    try {
      const info = await apiFetch('/api/product/debug');
      state.user = info;
      updateAuthUI();
    } catch (err) {
      console.warn('Session check failed:', err.message);
      setToken(null);
    }
  }

  // ==================== STOREFRONT & PRODUCTS ====================
  function populateCategorySelects() {
    const filterSelect = $('categoryFilterSelect');
    const sellerSelect = $('prodCategory');
    CATEGORIES.forEach((cat) => {
      const label = cat.replace(/_/g, ' ');
      const opt1 = document.createElement('option');
      opt1.value = cat;
      opt1.textContent = label;
      filterSelect.appendChild(opt1);

      const opt2 = document.createElement('option');
      opt2.value = cat;
      opt2.textContent = label;
      sellerSelect.appendChild(opt2);
    });
  }

  async function loadProducts(keyword = '') {
    const grid = $('productsGrid');
    try {
      const data = await apiFetch(`/api/product/search?keyword=${encodeURIComponent(keyword)}`);
      state.products = Array.isArray(data) ? data : [];
      renderProductsGrid();
      renderSellerProductsTable();
    } catch (err) {
      grid.innerHTML = `<div class="empty-state">Unable to load products: ${escapeHtml(err.message)}</div>`;
    }
  }

  function renderProductsGrid() {
    const grid = $('productsGrid');
    const catFilter = $('categoryFilterSelect').value;
    const statusFilter = $('statusFilterSelect').value;
    const sortBy = $('sortProductsSelect').value;

    let filtered = state.products.filter((p) => {
      if (catFilter !== 'ALL' && p.category !== catFilter) return false;
      if (statusFilter !== 'ALL' && p.status !== statusFilter) return false;
      return true;
    });

    filtered.sort((a, b) => {
      if (sortBy === 'price-asc') return Number(a.price) - Number(b.price);
      if (sortBy === 'price-desc') return Number(b.price) - Number(a.price);
      if (sortBy === 'rating-desc') return Number(b.averageRating || 0) - Number(a.averageRating || 0);
      return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
    });

    $('productCountLabel').textContent = `${filtered.length} product${filtered.length === 1 ? '' : 's'}`;

    if (filtered.length === 0) {
      grid.innerHTML = `<div class="empty-state">No products match your current filter. If you are a Seller, head to <strong>Seller Studio</strong> to add products!</div>`;
      return;
    }

    grid.innerHTML = filtered
      .map((p) => {
        const statusClass =
          p.status === 'AVAILABLE'
            ? 'badge-success'
            : p.status === 'SUSPENDED'
            ? 'badge-danger'
            : 'badge-warning';
        const avg = Number(p.averageRating || 0).toFixed(1);
        const revCount = Number(p.reviewCount || 0);
        const canAdd = p.status === 'AVAILABLE' && p.stockQuantity > 0;

        return `
          <article class="product-card">
            <div class="product-img-wrap">
              <img src="${escapeHtml(p.imageUrl || FALLBACK_IMG)}" alt="${escapeHtml(p.name)}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'" />
              <span class="badge ${statusClass} product-status-pill">${escapeHtml(p.status)}</span>
              <span class="product-category-pill">${escapeHtml((p.category || '').replace(/_/g, ' '))}</span>
            </div>
            <div class="product-body">
              <div class="product-brand">${escapeHtml(p.brand)}</div>
              <h3 class="product-title">${escapeHtml(p.name)}</h3>
              <p class="product-desc">${escapeHtml(p.description)}</p>
              <div class="product-meta-row">
                <span class="product-price">${formatCurrency(p.price)}</span>
                <span class="product-rating">★ ${avg} <small>(${revCount})</small></span>
              </div>
              <div class="product-stock-info">Stock: <strong>${p.stockQuantity}</strong> • ID: <code>${escapeHtml(String(p.id).slice(0, 8))}…</code></div>
              <div class="product-actions">
                <button type="button" class="btn btn-outline btn-sm" style="flex:1" data-inspect-product="${escapeHtml(p.id)}">Details &amp; Reviews</button>
                <button type="button" class="btn btn-primary btn-sm" style="flex:1" data-add-cart="${escapeHtml(p.id)}" ${!canAdd ? 'disabled' : ''}>
                  ${canAdd ? '+ Add to Cart' : 'Unavailable'}
                </button>
              </div>
            </div>
          </article>
        `;
      })
      .join('');
  }

  async function inspectProductDetail(productId) {
    try {
      const p = await apiFetch(`/api/products/${encodeURIComponent(productId)}`);
      $('modalProductTitle').textContent = p.name;
      const reviews = Array.isArray(p.reviews) ? p.reviews : [];

      $('modalProductBody').innerHTML = `
        <div class="two-col-layout">
          <div>
            <img src="${escapeHtml(p.imageUrl || FALLBACK_IMG)}" alt="${escapeHtml(p.name)}" style="width:100%;max-height:260px;object-fit:cover;border-radius:8px;" onerror="this.src='${FALLBACK_IMG}'" />
            <div class="mt-sm small text-muted">
              <div><strong>Product UUID:</strong> <code>${escapeHtml(p.id)}</code></div>
              <div><strong>Seller UUID:</strong> <code>${escapeHtml(p.sellerId)}</code></div>
              <div><strong>Created:</strong> ${formatDate(p.createdAt)}</div>
            </div>
          </div>
          <div>
            <span class="badge badge-role">${escapeHtml(p.category)}</span>
            <span class="badge ${p.status === 'AVAILABLE' ? 'badge-success' : 'badge-danger'}">${escapeHtml(p.status)}</span>
            <h2 style="margin:0.5rem 0 0.25rem">${escapeHtml(p.name)}</h2>
            <div class="text-muted small">Brand: <strong>${escapeHtml(p.brand)}</strong></div>
            <div class="product-price" style="margin:0.65rem 0">${formatCurrency(p.price)}</div>
            <p>${escapeHtml(p.description)}</p>
            <div class="summary-line"><span>Stock Quantity:</span> <strong>${p.stockQuantity}</strong></div>
            <div class="summary-line"><span>Average Rating:</span> <strong>★ ${Number(p.averageRating || 0).toFixed(2)} (${Number(p.reviewCount || 0)} reviews)</strong></div>
            <div class="product-actions mt-md">
              <button type="button" class="btn btn-primary btn-sm" id="modalAddToCartBtn" ${p.status !== 'AVAILABLE' || p.stockQuantity <= 0 ? 'disabled' : ''}>🛒 Add to Cart</button>
              <button type="button" class="btn btn-outline btn-sm" id="modalPreFillSuspendBtn">🛡️ Send to Admin Suspend</button>
            </div>
          </div>
        </div>
        <hr class="divider" />
        <h4>Customer Reviews (${reviews.length})</h4>
        ${
          reviews.length === 0
            ? '<p class="text-muted small">No reviews yet. Customers can review items from delivered orders.</p>'
            : `<div class="orders-list">${reviews
                .map(
                  (r) => `
                <div class="order-item-line">
                  <div>
                    <strong>${escapeHtml(r.userFirstName || 'Buyer')} ${escapeHtml(r.userLastName || '')}</strong>
                    <span class="product-rating" style="margin-left:0.5rem">${'★'.repeat(r.rating)}${'☆'.repeat(Math.max(0, 5 - r.rating))}</span>
                    <div class="small">${escapeHtml(r.comment)}</div>
                  </div>
                  <span class="small text-muted">${formatDate(r.createdAt)}</span>
                </div>
              `
                )
                .join('')}</div>`
        }
      `;

      $('productDetailDialog').showModal();

      const modalAddBtn = $('modalAddToCartBtn');
      if (modalAddBtn) {
        modalAddBtn.onclick = () => {
          addToCart(p.id, 1);
          $('productDetailDialog').close();
        };
      }
      const modalSuspendBtn = $('modalPreFillSuspendBtn');
      if (modalSuspendBtn) {
        modalSuspendBtn.onclick = () => {
          $('productDetailDialog').close();
          $('suspendProductIdInput').value = p.id;
          if (p.sellerId) $('suspendSellerIdInput').value = p.sellerId;
          switchTab('admin');
          showToast('Pre-filled Product & Seller UUIDs in Admin Console', 'info');
        };
      }
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  // ==================== SHOPPING CART & CHECKOUT ====================
  async function loadCart() {
    if (!state.token) {
      $('cartItemsContainer').innerHTML = `<div class="empty-state">Sign in to view and manage your cart.</div>`;
      return;
    }
    try {
      const cart = await apiFetch('/api/cart');
      state.cart = cart;
      renderCart();
    } catch (err) {
      $('cartItemsContainer').innerHTML = `<div class="empty-state">Could not load cart: ${escapeHtml(err.message)}</div>`;
    }
  }

  function renderCart() {
    const cart = state.cart;
    const items = cart && Array.isArray(cart.items) ? cart.items : [];
    const totalItems = cart ? Number(cart.totalItems || 0) : 0;
    const subtotal = cart ? Number(cart.total || 0) : 0;

    $('cartCountBadge').textContent = String(totalItems);
    $('cartTotalItemsLabel').textContent = `${totalItems} item${totalItems === 1 ? '' : 's'}`;
    updateCheckoutTotals(subtotal);

    const container = $('cartItemsContainer');
    if (items.length === 0) {
      container.innerHTML = `<div class="empty-state">Your cart is empty. Browse the Storefront and click "+ Add to Cart"!</div>`;
      return;
    }

    container.innerHTML = items
      .map(
        (item) => `
      <div class="cart-item-row">
        <div class="cart-item-info">
          <img class="cart-item-thumb" src="${escapeHtml(item.productImageUrl || FALLBACK_IMG)}" alt="${escapeHtml(item.productName)}" onerror="this.src='${FALLBACK_IMG}'" />
          <div>
            <strong>${escapeHtml(item.productName)}</strong>
            <div class="small text-muted">${formatCurrency(item.price)} each • Subtotal: <strong>${formatCurrency(item.subtotal)}</strong></div>
          </div>
        </div>
        <div class="cart-qty-controls">
          <button type="button" class="btn btn-outline btn-xs" data-cart-qty="${escapeHtml(item.productId)}" data-next-qty="${item.quantity - 1}">−</button>
          <span style="min-width:26px;text-align:center;font-weight:700">${item.quantity}</span>
          <button type="button" class="btn btn-outline btn-xs" data-cart-qty="${escapeHtml(item.productId)}" data-next-qty="${item.quantity + 1}">+</button>
          <button type="button" class="btn btn-danger btn-xs" data-cart-remove="${escapeHtml(item.productId)}" title="Remove item">🗑️</button>
        </div>
      </div>
    `
      )
      .join('');
  }

  function updateCheckoutTotals(subtotal = state.cart ? Number(state.cart.total || 0) : 0) {
    const shipping = Number($('orderShippingCost').value || 0);
    $('checkoutSubtotalDisplay').textContent = formatCurrency(subtotal);
    $('checkoutShippingDisplay').textContent = formatCurrency(shipping);
    $('checkoutGrandTotalDisplay').textContent = formatCurrency(subtotal + shipping);
  }

  async function addToCart(productId, quantity = 1) {
    if (!state.token) {
      $('authDialog').showModal();
      showToast('Please sign in to add items to your cart.', 'info');
      return;
    }
    try {
      const cart = await apiFetch('/api/cart', {
        method: 'POST',
        body: { productId, quantity }
      });
      state.cart = cart;
      renderCart();
      showToast('Item added to cart!', 'success');
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function updateCartQuantity(productId, quantity) {
    try {
      const cart = await apiFetch(`/api/cart/${encodeURIComponent(productId)}`, {
        method: 'PUT',
        body: { productId, quantity }
      });
      state.cart = cart;
      renderCart();
      showToast(quantity <= 0 ? 'Item removed from cart' : 'Cart quantity updated', 'success');
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function removeFromCart(productId) {
    try {
      const cart = await apiFetch(`/api/cart/${encodeURIComponent(productId)}`, {
        method: 'DELETE'
      });
      state.cart = cart;
      renderCart();
      showToast('Item removed from cart', 'success');
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  // ==================== ORDERS, TRACKING, CANCEL, RETURN & REVIEWS ====================
  async function loadOrders() {
    if (!state.token) {
      $('ordersListContainer').innerHTML = `<div class="empty-state">Sign in to view your orders.</div>`;
      return;
    }
    const status = $('orderStatusFilter').value;
    const endpoint =
      status === 'ALL'
        ? '/api/orders?page=0&size=20'
        : `/api/orders/status/${encodeURIComponent(status)}?page=0&size=20`;

    try {
      const pageData = await apiFetch(endpoint);
      const list = pageData && Array.isArray(pageData.content) ? pageData.content : [];
      state.orders = list;
      $('ordersCountBadge').textContent = `${list.length} order${list.length === 1 ? '' : 's'}`;
      renderOrdersList(list);
    } catch (err) {
      $('ordersListContainer').innerHTML = `<div class="empty-state">Failed to load orders: ${escapeHtml(err.message)}</div>`;
    }
  }

  function renderOrdersList(orders) {
    const container = $('ordersListContainer');
    if (!orders || orders.length === 0) {
      container.innerHTML = `<div class="empty-state">No orders found for the selected filter.</div>`;
      return;
    }

    container.innerHTML = orders
      .map((order) => {
        const items = Array.isArray(order.items) ? order.items : [];
        const canCancel = !['SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED'].includes(order.status);
        const isDelivered = order.status === 'DELIVERED';

        const statusBadgeClass =
          order.status === 'DELIVERED'
            ? 'badge-success'
            : order.status === 'CANCELLED' || order.status === 'RETURNED'
            ? 'badge-danger'
            : 'badge-role';

        return `
          <div class="order-card">
            <div class="order-card-header">
              <div>
                <strong>Order #<code>${escapeHtml(order.id)}</code></strong>
                <div class="small text-muted">
                  Placed: ${formatDate(order.createdAt)} • Tracking #: <code>${escapeHtml(order.trackingNumber)}</code>
                </div>
              </div>
              <div style="display:flex;gap:0.5rem;align-items:center">
                <span class="badge ${statusBadgeClass}">${escapeHtml(order.status)}</span>
                <strong>${formatCurrency(order.orderTotal)}</strong>
              </div>
            </div>

            <div class="order-items-sublist">
              ${items
                .map(
                  (item) => `
                <div class="order-item-line">
                  <div style="display:flex;align-items:center;gap:0.65rem">
                    <img class="cart-item-thumb" src="${escapeHtml(item.productImageUrl || FALLBACK_IMG)}" alt="${escapeHtml(item.productName)}" onerror="this.src='${FALLBACK_IMG}'" />
                    <div>
                      <strong>${escapeHtml(item.productName)}</strong>
                      <div class="small text-muted">Qty: ${item.quantity} × ${formatCurrency(item.price)} = <strong>${formatCurrency(item.subtotal)}</strong></div>
                      <div class="small text-muted">OrderItem UUID: <code>${escapeHtml(item.id)}</code></div>
                    </div>
                  </div>
                  <div>
                    <button
                      type="button"
                      class="btn btn-outline btn-xs"
                      data-review-item="${escapeHtml(item.id)}"
                      data-review-name="${escapeHtml(item.productName)}"
                      title="${isDelivered ? 'Write a product review' : 'Backend allows reviews once order is DELIVERED'}"
                    >
                      ⭐ Write Review
                    </button>
                  </div>
                </div>
              `
                )
                .join('')}
            </div>

            <div class="order-card-footer">
              <div class="small text-muted">
                Ship to: ${escapeHtml(order.shippingAddress)}, ${escapeHtml(order.city)}, ${escapeHtml(order.state)} ${escapeHtml(order.zipCode)}, ${escapeHtml(order.country)}
                ${order.returnReason ? ` • <strong>Return Reason:</strong> ${escapeHtml(order.returnReason)}` : ''}
              </div>
              <div style="display:flex;gap:0.45rem;flex-wrap:wrap">
                <button type="button" class="btn btn-outline btn-xs" data-track-order-id="${escapeHtml(order.id)}">📍 Track Order</button>
                <button type="button" class="btn btn-outline btn-xs" data-track-number="${escapeHtml(order.trackingNumber)}">🚚 Track #${escapeHtml(order.trackingNumber)}</button>
                ${
                  canCancel
                    ? `<button type="button" class="btn btn-danger btn-xs" data-cancel-order="${escapeHtml(order.id)}">✕ Cancel Order</button>`
                    : ''
                }
                <button type="button" class="btn btn-secondary btn-xs" data-return-order="${escapeHtml(order.id)}">↩️ Request Return</button>
              </div>
            </div>
          </div>
        `;
      })
      .join('');
  }

  function showTrackingResult(t) {
    $('trackingResultCard').classList.remove('hidden');
    $('trackingResultBody').innerHTML = `
      <div class="two-col-layout">
        <div>
          <div class="summary-line"><span>Order ID:</span> <code>${escapeHtml(t.id)}</code></div>
          <div class="summary-line"><span>Tracking Number:</span> <strong><code>${escapeHtml(t.trackingNumber)}</code></strong></div>
          <div class="summary-line"><span>Current Status:</span> <span class="badge badge-role">${escapeHtml(t.status)}</span></div>
          <div class="summary-line"><span>Last Updated:</span> <span>${formatDate(t.updatedAt || t.createdAt)}</span></div>
        </div>
        <div>
          <div class="summary-line"><span>Destination:</span> <span>${escapeHtml(t.shippingAddress)}, ${escapeHtml(t.city)}, ${escapeHtml(t.state)} ${escapeHtml(t.zipCode)}</span></div>
          <div class="summary-line"><span>Country:</span> <span>${escapeHtml(t.country)}</span></div>
          <div class="summary-line"><span>Carrier:</span> <span>${escapeHtml(t.carrier || 'Standard Express')}</span></div>
        </div>
      </div>
    `;
  }

  function openReturnOrderModal(orderId) {
    $('orderActionModalTitle').textContent = `Return Order (${orderId.slice(0, 8)}…)`;
    $('orderActionModalBody').innerHTML = `
      <form id="modalReturnOrderForm" class="stack-form">
        <p class="small text-muted">Note: Backend validates that the order status is <code>DELIVERED</code> before accepting a return.</p>
        <div class="form-group">
          <label for="modalReturnReason">Return Reason *</label>
          <select id="modalReturnReason" required>
            ${RETURN_REASONS.map((r) => `<option value="${r}">${r.replace(/_/g, ' ')}</option>`).join('')}
          </select>
        </div>
        <div class="form-group">
          <label for="modalReturnComment">Additional Comments</label>
          <textarea id="modalReturnComment" rows="3" maxlength="1000" placeholder="Describe the issue..."></textarea>
        </div>
        <button type="submit" class="btn btn-primary btn-block">Submit Return Request</button>
      </form>
    `;
    $('orderActionDialog').showModal();

    $('modalReturnOrderForm').onsubmit = async (e) => {
      e.preventDefault();
      try {
        await apiFetch(`/api/orders/${encodeURIComponent(orderId)}/return`, {
          method: 'PUT',
          body: {
            returnReason: $('modalReturnReason').value,
            comment: $('modalReturnComment').value.trim()
          }
        });
        $('orderActionDialog').close();
        showToast('Return request submitted!', 'success');
        loadOrders();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };
  }

  function openReviewItemModal(orderItemId, productName) {
    $('orderActionModalTitle').textContent = `Review: ${productName}`;
    $('orderActionModalBody').innerHTML = `
      <form id="modalReviewItemForm" class="stack-form">
        <p class="small text-muted">Submitting to <code>POST /api/orders/items/${escapeHtml(orderItemId)}/review</code></p>
        <div class="form-group">
          <label for="modalReviewRating">Star Rating (1–5) *</label>
          <select id="modalReviewRating" required>
            <option value="5">★★★★★ (5 - Excellent)</option>
            <option value="4">★★★★☆ (4 - Good)</option>
            <option value="3">★★★☆☆ (3 - Average)</option>
            <option value="2">★★☆☆☆ (2 - Fair)</option>
            <option value="1">★☆☆☆☆ (1 - Poor)</option>
          </select>
        </div>
        <div class="form-group">
          <label for="modalReviewComment">Your Review Comment *</label>
          <textarea id="modalReviewComment" rows="3" maxlength="1000" placeholder="Share your experience with this product..." required></textarea>
        </div>
        <button type="submit" class="btn btn-primary btn-block">Publish Review</button>
      </form>
    `;
    $('orderActionDialog').showModal();

    $('modalReviewItemForm').onsubmit = async (e) => {
      e.preventDefault();
      try {
        await apiFetch(`/api/orders/items/${encodeURIComponent(orderItemId)}/review`, {
          method: 'POST',
          body: {
            rating: Number($('modalReviewRating').value),
            comment: $('modalReviewComment').value.trim()
          }
        });
        $('orderActionDialog').close();
        showToast('Review submitted and product rating updated!', 'success');
        loadProducts();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };
  }

  // ==================== BUYER ADDRESSES ====================
  async function loadAddresses() {
    if (!state.token) return;
    try {
      const list = await apiFetch('/api/user/addresses');
      state.addresses = Array.isArray(list) ? list : [];
      renderAddresses();
    } catch (err) {
      $('addressesListContainer').innerHTML = `<div class="empty-state">${escapeHtml(err.message)}</div>`;
    }
  }

  function renderAddresses() {
    const container = $('addressesListContainer');
    if (state.addresses.length === 0) {
      container.innerHTML = `<div class="empty-state">No saved addresses yet. Use the form above to add one!</div>`;
      return;
    }

    container.innerHTML = state.addresses
      .map((a) => {
        const isDef = Boolean(a.default || a.isDefault);
        return `
        <div class="address-card">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <strong>${escapeHtml(a.fullName)}</strong>
            ${isDef ? '<span class="badge badge-success">Default</span>' : ''}
          </div>
          <div class="small">${escapeHtml(a.street)}</div>
          <div class="small">${escapeHtml(a.city)}, ${escapeHtml(a.state)} ${escapeHtml(a.zipCode)}</div>
          ${a.landmark ? `<div class="small text-muted">Landmark: ${escapeHtml(a.landmark)}</div>` : ''}
          <div class="small text-muted">Phone: ${escapeHtml(a.phone)}</div>
          <div style="display:flex;gap:0.4rem;margin-top:0.5rem">
            <button type="button" class="btn btn-outline btn-xs" data-edit-address="${escapeHtml(a.id)}">✏️ Edit</button>
            <button type="button" class="btn btn-danger btn-xs" data-delete-address="${escapeHtml(a.id)}">🗑️ Delete</button>
          </div>
        </div>
      `;
      })
      .join('');
  }

  function resetAddressForm() {
    $('editAddressId').value = '';
    $('addressForm').reset();
    $('addressFormTitle').textContent = '📍 Add Buyer Address';
    $('addressSubmitBtn').textContent = '💾 Save Address';
    $('cancelAddressEditBtn').classList.add('hidden');
  }

  // ==================== SELLER STUDIO ====================
  function renderSellerProductsTable() {
    const wrap = $('sellerProductsTableWrap');
    if (state.products.length === 0) {
      wrap.innerHTML = `<div class="empty-state">No products in catalog yet. Use the form on the left to add your first product!</div>`;
      return;
    }

    wrap.innerHTML = `
      <table class="data-table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Category</th>
            <th>Price</th>
            <th>Stock</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          ${state.products
            .map(
              (p) => `
            <tr>
              <td>
                <strong>${escapeHtml(p.name)}</strong>
                <div class="small text-muted">${escapeHtml(p.brand)} • <code>${escapeHtml(String(p.id).slice(0, 8))}…</code></div>
              </td>
              <td><span class="badge">${escapeHtml(p.category)}</span></td>
              <td><strong>${formatCurrency(p.price)}</strong></td>
              <td>${p.stockQuantity}</td>
              <td><span class="badge ${p.status === 'AVAILABLE' ? 'badge-success' : 'badge-warning'}">${escapeHtml(p.status)}</span></td>
              <td style="white-space:nowrap">
                <button type="button" class="btn btn-outline btn-xs" data-seller-edit="${escapeHtml(p.id)}">✏️ Edit</button>
                <button type="button" class="btn btn-danger btn-xs" data-seller-delete="${escapeHtml(p.id)}">🗑️ Delete</button>
              </td>
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>
    `;
  }

  function resetSellerProductForm() {
    $('editProductId').value = '';
    $('sellerProductForm').reset();
    $('sellerProductFormTitle').textContent = '➕ Add New Product';
    $('sellerProductSubmitBtn').textContent = '🚀 Publish Product';
    $('cancelProductEditBtn').classList.add('hidden');
  }

  // ==================== ADMIN CONSOLE ====================
  async function loadAdminUsers() {
    const role = $('adminRoleFilter').value;
    const gender = $('adminGenderFilter').value;
    const age = $('adminAgeFilter').value;
    const page = $('adminPageInput').value || 0;

    const params = new URLSearchParams();
    if (role) params.set('role', role);
    if (gender) params.set('gender', gender);
    if (age) params.set('age', age);
    params.set('page', page);
    params.set('size', '20');

    try {
      const pageData = await apiFetch(`/api/admin/users?${params.toString()}`);
      const users = pageData && Array.isArray(pageData.content) ? pageData.content : [];
      renderAdminUsersTable(users, pageData);
    } catch (err) {
      $('adminUsersTableContainer').innerHTML = `<div class="empty-state">${escapeHtml(err.message)}</div>`;
      showToast(err.message, 'error');
    }
  }

  function renderAdminUsersTable(users, pageMeta) {
    const container = $('adminUsersTableContainer');
    if (users.length === 0) {
      container.innerHTML = `<div class="empty-state">No users matched the selected filters.</div>`;
      return;
    }

    container.innerHTML = `
      <div class="small text-muted mb-md">
        Showing ${users.length} of ${pageMeta?.totalElements ?? users.length} total users (Page ${(pageMeta?.number ?? 0) + 1} of ${pageMeta?.totalPages || 1})
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th>User / UUID</th>
            <th>Email &amp; Phone</th>
            <th>Role</th>
            <th>Demographics</th>
            <th>Status</th>
            <th>Last Login</th>
            <th>Admin Action</th>
          </tr>
        </thead>
        <tbody>
          ${users
            .map(
              (u) => `
            <tr>
              <td>
                <strong>${escapeHtml(u.firstName)} ${escapeHtml(u.lastName)}</strong>
                <div class="small text-muted"><code>${escapeHtml(u.id)}</code></div>
              </td>
              <td>
                <div>${escapeHtml(u.email)}</div>
                <div class="small text-muted">${escapeHtml(u.phone || '—')}</div>
              </td>
              <td><span class="badge badge-role">${escapeHtml(u.role)}</span></td>
              <td class="small">${escapeHtml(u.gender || '—')} • Age: ${u.age ?? '—'}</td>
              <td>
                <span class="badge ${u.emailVerified ? 'badge-success' : 'badge-warning'}">${u.emailVerified ? 'Verified' : 'Unverified'}</span>
                ${u.suspended ? '<span class="badge badge-danger">Suspended</span>' : ''}
              </td>
              <td class="small">${formatDate(u.lastLoginAt)}</td>
              <td>
                ${
                  u.role === 'SELLER'
                    ? `<button type="button" class="btn btn-danger btn-xs" data-prefill-suspend-seller="${escapeHtml(u.id)}">🚫 Suspend Seller</button>`
                    : '<span class="small text-muted">—</span>'
                }
              </td>
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>
    `;
  }

  // ==================== EVENT LISTENERS ====================
  function bindEvents() {
    // Brand home click
    $('brandHomeBtn').addEventListener('click', () => switchTab('shop'));

    // Main tab bar
    $('mainNavTabs').addEventListener('click', (e) => {
      const btn = e.target.closest('.nav-tab[data-tab]');
      if (btn) switchTab(btn.dataset.tab);
    });

    // Quick cart button
    $('cartQuickBtn').addEventListener('click', () => switchTab('cart'));

    // Auth dialog open/close & inner tab switcher
    $('openAuthModalBtn').addEventListener('click', () => $('authDialog').showModal());
    $('closeAuthDialogBtn').addEventListener('click', () => $('authDialog').close());
    $('closeProductModalBtn').addEventListener('click', () => $('productDetailDialog').close());
    $('closeOrderActionModalBtn').addEventListener('click', () => $('orderActionDialog').close());

    document.querySelectorAll('.auth-tab').forEach((tabBtn) => {
      tabBtn.addEventListener('click', () => {
        const target = tabBtn.dataset.authTab;
        document.querySelectorAll('.auth-tab').forEach((b) => b.classList.toggle('active', b === tabBtn));
        document.querySelectorAll('.auth-panel').forEach((p) => p.classList.toggle('active', p.id === `auth-panel-${target}`));
      });
    });

    // Logout
    $('logoutBtn').addEventListener('click', () => {
      setToken(null);
      showToast('Signed out successfully.', 'info');
      switchTab('shop');
    });

    // Login Form (`POST /api/auth/login`)
    $('loginForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const token = await apiFetch('/api/auth/login', {
          method: 'POST',
          body: {
            email: $('loginEmail').value.trim(),
            password: $('loginPassword').value
          }
        });
        setToken(typeof token === 'string' ? token.trim() : token);
        $('authDialog').close();
        showToast('Signed in successfully!', 'success');
        await fetchSessionDebug();
        loadCart();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Register Form (`POST /api/user/register`)
    $('registerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const email = $('regEmail').value.trim();
        await apiFetch('/api/user/register', {
          method: 'POST',
          body: {
            firstName: $('regFirstName').value.trim(),
            lastName: $('regLastName').value.trim(),
            email,
            phone: $('regPhone').value.trim(),
            password: $('regPassword').value,
            gender: $('regGender').value,
            age: Number($('regAge').value || 22)
          }
        });
        showToast('Account registered! Please sign in.', 'success');
        $('loginEmail').value = email;
        document.querySelector('.auth-tab[data-auth-tab="login"]').click();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Forgot Password Form (`POST /api/auth/forgot-password?email=`)
    $('forgotPasswordForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = $('forgotEmailInput').value.trim();
      try {
        const msg = await apiFetch(`/api/auth/forgot-password?email=${encodeURIComponent(email)}`, {
          method: 'POST'
        });
        $('resetEmailInput').value = email;
        showToast(typeof msg === 'string' ? msg : 'Password reset OTP sent!', 'success');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Reset Password Form (`POST /api/auth/reset-password`)
    $('resetPasswordForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const msg = await apiFetch('/api/auth/reset-password', {
          method: 'POST',
          body: {
            email: $('resetEmailInput').value.trim(),
            otp: $('resetOtpInput').value.trim(),
            newPassword: $('resetNewPasswordInput').value
          }
        });
        showToast(typeof msg === 'string' ? msg : 'Password updated!', 'success');
        $('loginEmail').value = $('resetEmailInput').value.trim();
        document.querySelector('.auth-tab[data-auth-tab="login"]').click();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Global Search & Storefront Filters
    $('globalSearchForm').addEventListener('submit', (e) => {
      e.preventDefault();
      switchTab('shop');
      loadProducts($('globalSearchInput').value.trim());
    });

    $('clearSearchBtn').addEventListener('click', () => {
      $('globalSearchInput').value = '';
      $('categoryFilterSelect').value = 'ALL';
      $('statusFilterSelect').value = 'ALL';
      loadProducts('');
    });

    $('refreshProductsBtn').addEventListener('click', () => loadProducts($('globalSearchInput').value.trim()));
    $('categoryFilterSelect').addEventListener('change', renderProductsGrid);
    $('statusFilterSelect').addEventListener('change', renderProductsGrid);
    $('sortProductsSelect').addEventListener('change', renderProductsGrid);

    $('directProductLookupBtn').addEventListener('click', () => {
      const id = $('directProductIdInput').value.trim();
      if (!id) {
        showToast('Enter a Product UUID first', 'info');
        return;
      }
      inspectProductDetail(id);
    });

    // Product Grid Delegation
    $('productsGrid').addEventListener('click', (e) => {
      const inspectBtn = e.target.closest('[data-inspect-product]');
      if (inspectBtn) {
        inspectProductDetail(inspectBtn.dataset.inspectProduct);
        return;
      }
      const addBtn = e.target.closest('[data-add-cart]');
      if (addBtn) {
        addToCart(addBtn.dataset.addCart, 1);
      }
    });

    // Cart Actions & Checkout
    $('refreshCartBtn').addEventListener('click', loadCart);

    $('viewPageableCartBtn').addEventListener('click', async () => {
      const box = $('cartPageableOutput');
      if (!box.classList.contains('hidden')) {
        box.classList.add('hidden');
        return;
      }
      try {
        const page = await apiFetch('/api/cart/items?page=0&size=20');
        box.textContent = JSON.stringify(page, null, 2);
        box.classList.remove('hidden');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('cartItemsContainer').addEventListener('click', (e) => {
      const qtyBtn = e.target.closest('[data-cart-qty]');
      if (qtyBtn) {
        updateCartQuantity(qtyBtn.dataset.cartQty, Number(qtyBtn.dataset.nextQty));
        return;
      }
      const rmBtn = e.target.closest('[data-cart-remove]');
      if (rmBtn) {
        removeFromCart(rmBtn.dataset.cartRemove);
      }
    });

    $('orderShippingCost').addEventListener('input', () => updateCheckoutTotals());

    // Saved Address Autofill in Checkout
    $('autofillAddressBtn').addEventListener('click', async () => {
      await loadAddresses();
      const box = $('savedAddressPickerBox');
      const sel = $('savedAddressSelect');
      if (state.addresses.length === 0) {
        showToast('No saved Buyer addresses found. Add one in the Account tab or fill the fields manually.', 'info');
        return;
      }
      sel.innerHTML = `<option value="">-- Select address --</option>` +
        state.addresses
          .map((a, idx) => `<option value="${idx}">${escapeHtml(a.fullName)} — ${escapeHtml(a.street)}, ${escapeHtml(a.city)} (${escapeHtml(a.zipCode)})</option>`)
          .join('');
      box.classList.remove('hidden');
    });

    $('savedAddressSelect').addEventListener('change', (e) => {
      const idx = e.target.value;
      if (idx === '') return;
      const addr = state.addresses[Number(idx)];
      if (!addr) return;
      $('orderShippingAddress').value = addr.street || '';
      $('orderCity').value = addr.city || '';
      $('orderState').value = addr.state || '';
      // Ensure 5-digit ZIP pattern for PlaceOrderRequest validation (\d{5}(-\d{4})?)
      const rawZip = String(addr.zipCode || '').trim();
      $('orderZipCode').value = /^\d{5}(-\d{4})?$/.test(rawZip) ? rawZip : rawZip.slice(0, 5).padEnd(5, '0');
      $('orderPhoneNumber').value = addr.phone || '';
    });

    // Place Order (`POST /api/orders`)
    $('placeOrderForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const order = await apiFetch('/api/orders', {
          method: 'POST',
          body: {
            paymentMethod: $('orderPaymentMethod').value,
            shippingCost: Number($('orderShippingCost').value || 0),
            shippingAddress: $('orderShippingAddress').value.trim(),
            city: $('orderCity').value.trim(),
            state: $('orderState').value.trim(),
            zipCode: $('orderZipCode').value.trim(),
            country: $('orderCountry').value.trim(),
            phoneNumber: $('orderPhoneNumber').value.trim(),
            deliveryNotes: $('orderDeliveryNotes').value.trim()
          }
        });
        showToast(`Order placed! Tracking #: ${order.trackingNumber}`, 'success');
        await loadCart();
        await loadProducts();
        switchTab('orders');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    // Orders & Tracking Tab
    $('refreshOrdersBtn').addEventListener('click', loadOrders);
    $('orderStatusFilter').addEventListener('change', loadOrders);
    $('closeTrackingCardBtn').addEventListener('click', () => $('trackingResultCard').classList.add('hidden'));

    $('trackByNumberForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const trk = $('trackingNumberInput').value.trim();
      if (!trk) return;
      try {
        const res = await apiFetch(`/api/orders/track/${encodeURIComponent(trk)}`);
        showTrackingResult(res);
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('lookupOrderByIdForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const orderId = $('lookupOrderIdInput').value.trim();
      if (!orderId) return;
      try {
        const order = await apiFetch(`/api/orders/${encodeURIComponent(orderId)}`);
        renderOrdersList([order]);
        showToast('Loaded order details', 'success');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('trackOrderIdBtn').addEventListener('click', async () => {
      const orderId = $('lookupOrderIdInput').value.trim();
      if (!orderId) {
        showToast('Enter an Order UUID first', 'info');
        return;
      }
      try {
        const res = await apiFetch(`/api/orders/${encodeURIComponent(orderId)}/track`);
        showTrackingResult(res);
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('ordersListContainer').addEventListener('click', async (e) => {
      const trackIdBtn = e.target.closest('[data-track-order-id]');
      if (trackIdBtn) {
        try {
          const res = await apiFetch(`/api/orders/${encodeURIComponent(trackIdBtn.dataset.trackOrderId)}/track`);
          showTrackingResult(res);
        } catch (err) {
          showToast(err.message, 'error');
        }
        return;
      }

      const trackNumBtn = e.target.closest('[data-track-number]');
      if (trackNumBtn) {
        try {
          const res = await apiFetch(`/api/orders/track/${encodeURIComponent(trackNumBtn.dataset.trackNumber)}`);
          showTrackingResult(res);
        } catch (err) {
          showToast(err.message, 'error');
        }
        return;
      }

      const cancelBtn = e.target.closest('[data-cancel-order]');
      if (cancelBtn) {
        const id = cancelBtn.dataset.cancelOrder;
        try {
          await apiFetch(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: 'PUT' });
          showToast('Order cancelled and product stock restored.', 'success');
          loadOrders();
          loadProducts();
        } catch (err) {
          showToast(err.message, 'error');
        }
        return;
      }

      const returnBtn = e.target.closest('[data-return-order]');
      if (returnBtn) {
        openReturnOrderModal(returnBtn.dataset.returnOrder);
        return;
      }

      const reviewBtn = e.target.closest('[data-review-item]');
      if (reviewBtn) {
        openReviewItemModal(reviewBtn.dataset.reviewItem, reviewBtn.dataset.reviewName);
      }
    });

    // Account, Email OTP, Become Seller & Buyer Addresses
    $('refreshAccountBtn').addEventListener('click', () => {
      fetchSessionDebug();
      loadAddresses();
    });

    $('sendEmailOtpBtn').addEventListener('click', async () => {
      const btn = $('sendEmailOtpBtn');
      const statusEl = $('emailOtpStatusMsg');
      btn.disabled = true;
      btn.textContent = '⏳ Sending OTP to your email...';
      if (statusEl) statusEl.innerHTML = '<span class="text-muted">Sending OTP via SMTP... do not click again or it will overwrite the code.</span>';
      try {
        const msg = await apiFetch('/api/user/email-verification');
        showToast(typeof msg === 'string' ? msg : 'Verification OTP sent to your email!', 'success');
        if (statusEl) {
          statusEl.innerHTML = '<span style="color:var(--success);font-weight:600">✓ New 8-digit OTP sent! Check the newest message at the bottom of your Gmail thread.</span>';
        }
        btn.textContent = '📨 Resend Verification OTP';
        btn.disabled = false;
      } catch (err) {
        if (statusEl) {
          statusEl.innerHTML = `<span style="color:var(--danger);font-weight:600">✕ ${escapeHtml(err.message)}</span>`;
        }
        showToast(err.message, 'error');
        btn.textContent = '📨 Send Verification OTP to My Email';
        btn.disabled = false;
      }
    });

    $('verifyEmailOtpForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const statusEl = $('emailOtpStatusMsg');
      const verifyBtn = $('verifyEmailOtpBtn');
      const otpValue = $('emailOtpInput').value.replace(/\s+/g, '');
      if (verifyBtn) verifyBtn.disabled = true;
      try {
        const msg = await apiFetch('/api/user/verify-otp', {
          method: 'POST',
          body: { otp: otpValue }
        });
        showToast(typeof msg === 'string' ? msg : 'Email verified!', 'success');
        if (statusEl) {
          statusEl.innerHTML = `<span style="color:var(--success);font-weight:600">✅ ${escapeHtml(typeof msg === 'string' ? msg : 'Your Email Has Been Verified!')}</span>`;
        }
        $('emailOtpInput').value = '';
      } catch (err) {
        if (statusEl) {
          statusEl.innerHTML = `<span style="color:var(--danger);font-weight:600">✕ ${escapeHtml(err.message)} — Make sure you are using the most recent 8-digit OTP email (not an older message in the Gmail thread).</span>`;
        }
        showToast(err.message, 'error');
      } finally {
        if (verifyBtn) verifyBtn.disabled = false;
      }
    });

    $('becomeSellerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        const res = await apiFetch('/api/seller/become', {
          method: 'POST',
          body: {
            storeName: $('sellerStoreName').value.trim(),
            businessAddress: $('sellerBusinessAddress').value.trim(),
            storeLogoUrl: $('sellerStoreLogoUrl').value.trim()
          }
        });
        $('sellerUpgradeResult').innerHTML = `
          <div class="badge badge-success">Store Created: ${escapeHtml(res.storeName)} (Status: ${escapeHtml(res.status)})</div>
        `;
        showToast('Congratulations! Your account is now upgraded to SELLER.', 'success');
        await fetchSessionDebug();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('refreshAddressesBtn').addEventListener('click', loadAddresses);
    $('cancelAddressEditBtn').addEventListener('click', resetAddressForm);

    $('addressForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const editId = $('editAddressId').value.trim();
      const isDefault = $('addrIsDefault').checked;
      const payload = {
        fullName: $('addrFullName').value.trim(),
        phone: $('addrPhone').value.trim(),
        street: $('addrStreet').value.trim(),
        city: $('addrCity').value.trim(),
        state: $('addrState').value.trim(),
        zipCode: $('addrZipCode').value.trim(),
        landmark: $('addrLandmark').value.trim(),
        default: isDefault,
        isDefault: isDefault
      };

      try {
        if (editId) {
          await apiFetch(`/api/user/addresses/${encodeURIComponent(editId)}`, {
            method: 'PUT',
            body: payload
          });
          showToast('Address updated!', 'success');
        } else {
          await apiFetch('/api/user/addresses', {
            method: 'POST',
            body: payload
          });
          showToast('Address added!', 'success');
        }
        resetAddressForm();
        loadAddresses();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('addressesListContainer').addEventListener('click', async (e) => {
      const editBtn = e.target.closest('[data-edit-address]');
      if (editBtn) {
        const addr = state.addresses.find((a) => String(a.id) === editBtn.dataset.editAddress);
        if (!addr) return;
        $('editAddressId').value = addr.id;
        $('addrFullName').value = addr.fullName || '';
        $('addrPhone').value = addr.phone || '';
        $('addrStreet').value = addr.street || '';
        $('addrCity').value = addr.city || '';
        $('addrState').value = addr.state || '';
        $('addrZipCode').value = addr.zipCode || '';
        $('addrLandmark').value = addr.landmark || '';
        $('addrIsDefault').checked = Boolean(addr.default || addr.isDefault);
        $('addressFormTitle').textContent = '✏️ Edit Buyer Address';
        $('addressSubmitBtn').textContent = '💾 Update Address';
        $('cancelAddressEditBtn').classList.remove('hidden');
        return;
      }

      const delBtn = e.target.closest('[data-delete-address]');
      if (delBtn) {
        try {
          await apiFetch(`/api/user/addresses/${encodeURIComponent(delBtn.dataset.deleteAddress)}`, {
            method: 'DELETE'
          });
          showToast('Address deleted', 'success');
          loadAddresses();
        } catch (err) {
          showToast(err.message, 'error');
        }
      }
    });

    // Seller Studio Events
    $('refreshSellerProductsBtn').addEventListener('click', () => loadProducts(''));
    $('cancelProductEditBtn').addEventListener('click', resetSellerProductForm);

    $('sellerProductForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const editId = $('editProductId').value.trim();
      const payload = {
        name: $('prodName').value.trim(),
        brand: $('prodBrand').value.trim(),
        category: $('prodCategory').value,
        price: Number($('prodPrice').value),
        stockQuantity: Number($('prodStock').value),
        imageUrl: $('prodImageUrl').value.trim(),
        description: $('prodDescription').value.trim()
      };

      try {
        if (editId) {
          await apiFetch(`/api/product/${encodeURIComponent(editId)}`, {
            method: 'PUT',
            body: payload
          });
          showToast('Product updated!', 'success');
        } else {
          await apiFetch('/api/product', {
            method: 'POST',
            body: payload
          });
          showToast('Product created!', 'success');
        }
        resetSellerProductForm();
        loadProducts('');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('sellerProductsTableWrap').addEventListener('click', async (e) => {
      const editBtn = e.target.closest('[data-seller-edit]');
      if (editBtn) {
        const p = state.products.find((item) => String(item.id) === editBtn.dataset.sellerEdit);
        if (!p) return;
        $('editProductId').value = p.id;
        $('prodName').value = p.name || '';
        $('prodBrand').value = p.brand || '';
        $('prodCategory').value = p.category || 'ELECTRONICS';
        $('prodPrice').value = p.price;
        $('prodStock').value = p.stockQuantity;
        $('prodImageUrl').value = p.imageUrl || '';
        $('prodDescription').value = p.description || '';
        $('sellerProductFormTitle').textContent = `✏️ Update Product (${String(p.id).slice(0, 8)}…)`;
        $('sellerProductSubmitBtn').textContent = '💾 Save Changes';
        $('cancelProductEditBtn').classList.remove('hidden');
        return;
      }

      const delBtn = e.target.closest('[data-seller-delete]');
      if (delBtn) {
        try {
          await apiFetch(`/api/product/${encodeURIComponent(delBtn.dataset.sellerDelete)}`, {
            method: 'DELETE'
          });
          showToast('Product deleted!', 'success');
          loadProducts('');
        } catch (err) {
          showToast(err.message, 'error');
        }
      }
    });

    // Admin Console Events
    $('adminLoadUsersBtn').addEventListener('click', loadAdminUsers);
    $('adminUsersFilterForm').addEventListener('submit', (e) => {
      e.preventDefault();
      loadAdminUsers();
    });

    $('adminSuspendProductForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = $('suspendProductIdInput').value.trim();
      const reason = $('suspendProductReasonInput').value.trim();
      try {
        await apiFetch(`/api/admin/product/${encodeURIComponent(id)}/suspend`, {
          method: 'PUT',
          body: { reason }
        });
        showToast('Product suspended by Admin.', 'success');
        $('adminSuspendProductForm').reset();
        loadProducts('');
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('adminSuspendSellerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = $('suspendSellerIdInput').value.trim();
      const reason = $('suspendSellerReasonInput').value.trim();
      try {
        await apiFetch(`/api/admin/seller/${encodeURIComponent(id)}/suspend`, {
          method: 'PUT',
          body: { reason }
        });
        showToast('Seller & associated User account suspended.', 'success');
        $('adminSuspendSellerForm').reset();
        loadAdminUsers();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });

    $('adminUsersTableContainer').addEventListener('click', (e) => {
      const btn = e.target.closest('[data-prefill-suspend-seller]');
      if (btn) {
        $('suspendSellerIdInput').value = btn.dataset.prefillSuspendSeller;
        $('suspendSellerReasonInput').focus();
        showToast('Pre-filled Seller UUID in Suspend Seller form above.', 'info');
      }
    });
  }

  // ==================== INITIALIZATION ====================
  async function init() {
    populateCategorySelects();
    bindEvents();
    updateAuthUI();
    await loadProducts('');
    if (state.token) {
      await fetchSessionDebug();
      await loadCart();
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
