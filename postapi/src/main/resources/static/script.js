const API_URL = '/api/posts';
const MAX_CONTENT_LENGTH = 280;
const form = document.querySelector('#post-form');
const contentInput = document.querySelector('#content');
const characterCount = document.querySelector('#character-count');
const postsList = document.querySelector('#posts-list');
const emptyState = document.querySelector('#empty-state');
const loadingState = document.querySelector('#loading-state');
const postCount = document.querySelector('#post-count');
const message = document.querySelector('#message');
const searchForm = document.querySelector('#search-form');
const searchInput = document.querySelector('#search-input');
const clearSearch = document.querySelector('#clear-search');
const sortBy = document.querySelector('#sort-by');
const sortDirection = document.querySelector('#sort-direction');
const pageSize = document.querySelector('#page-size');
const previousPage = document.querySelector('#previous-page');
const nextPage = document.querySelector('#next-page');
const pageStatus = document.querySelector('#page-status');
const cancelEdit = document.querySelector('#cancel-edit');
const submitButton = document.querySelector('#submit-button');
const formMode = document.querySelector('#form-mode');
const authForm = document.querySelector('#auth-form');
const authUsername = document.querySelector('#auth-username');
const authPassword = document.querySelector('#auth-password');
const authUser = document.querySelector('#auth-user');
const authIdentity = document.querySelector('#auth-identity');
const logoutButton = document.querySelector('#logout-button');
const composerPanel = document.querySelector('#composer-panel');
let editingId = null;
let currentRequestUrl = API_URL;
let currentPage = 0;
let totalPages = 0;
let accessToken = null;
let currentUser = null;
let refreshInFlight = null;

function showMessage(text, type = '') {
    message.textContent = text;
    message.className = `message ${type}`;
}

function updateCounter() {
    characterCount.textContent = `${contentInput.value.length}/${MAX_CONTENT_LENGTH}`;
    characterCount.classList.toggle('limit-warning', contentInput.value.length >= MAX_CONTENT_LENGTH);
}

function formatDate(value) {
    if (!value) return 'Date unavailable';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString();
}

function createPostCard(post, index) {
    const card = document.createElement('article');
    card.className = 'post-card';
    card.style.animationDelay = `${Math.min(index * 45, 250)}ms`;

    const content = document.createElement('p');
    content.className = 'post-content';
    content.textContent = post.content || '';

    const meta = document.createElement('div');
    meta.className = 'post-meta';

    const date = document.createElement('time');
    date.dateTime = post.createdAt || '';
    date.textContent = formatDate(post.createdAt);

    if (canManagePost(post)) {
        const actions = document.createElement('div');
        actions.className = 'card-actions';
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'button button-quiet edit-button';
        edit.dataset.id = post.id;
        edit.textContent = 'Edit';
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'button button-quiet delete-button';
        remove.dataset.id = post.id;
        remove.textContent = 'Delete';
        actions.append(edit, remove);
        meta.append(date, actions);
    } else {
        meta.append(date);
    }
    card.append(content, meta);
    return card;
}

function canManagePost(post) {
    return currentUser && (currentUser.roles?.includes('ADMIN') || post.ownerId === currentUser.id);
}

function updateAuthState(user) {
    currentUser = user || null;
    authForm.classList.toggle('hidden', Boolean(currentUser));
    authUser.classList.toggle('hidden', !currentUser);
    composerPanel.classList.toggle('hidden', !currentUser);
    authIdentity.textContent = currentUser
        ? `${currentUser.username} · ${currentUser.roles.join(', ')}`
        : '';
    renderPosts({ items: [] });
}

function renderPosts(page) {
    const safePosts = Array.isArray(page?.items) ? page.items : [];
    currentPage = page?.page || 0;
    totalPages = page?.totalPages || 0;
    postCount.textContent = page?.totalElements || 0;
    postsList.replaceChildren(...safePosts.map(createPostCard));
    emptyState.classList.toggle('hidden', safePosts.length > 0);
    pageStatus.textContent = totalPages ? `Page ${currentPage + 1} of ${totalPages}` : '0 results';
    previousPage.disabled = currentPage === 0;
    nextPage.disabled = totalPages === 0 || currentPage >= totalPages - 1;
}

function getErrorMessage(status, body) {
    if (body.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
        const details = Object.values(body.data).filter(Boolean).join(' ');
        if (details) return details;
    }
    if (body.message) return body.message;
    if (status === 401) return 'Please log in again to continue.';
    if (status === 403) return 'You do not have permission to do that.';
    if (status === 400) return 'Please check the post content and try again.';
    if (status === 404) return 'That post could not be found. Refresh the list and try again.';
    return 'The request could not be completed. Please try again.';
}

async function refreshAccessToken() {
    if (refreshInFlight) return refreshInFlight;
    refreshInFlight = (async () => {
        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                credentials: 'include',
                headers: { Accept: 'application/json', 'X-CSRF-Protection': '1' }
            });
            const body = await response.json().catch(() => ({}));
            if (!response.ok || !body.data?.accessToken) {
                accessToken = null;
                updateAuthState(null);
                return false;
            }
            accessToken = body.data.accessToken;
            updateAuthState(body.data.user);
            return true;
        } catch (error) {
            accessToken = null;
            updateAuthState(null);
            return false;
        }
    })().finally(() => {
        refreshInFlight = null;
    });
    return refreshInFlight;
}

async function request(url, options = {}, retryAfterRefresh = true) {
    let response;
    const headers = { Accept: 'application/json', 'Content-Type': 'application/json', ...options.headers };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    if (['POST', 'PUT', 'DELETE'].includes((options.method || 'GET').toUpperCase())) {
        headers['X-CSRF-Protection'] = '1';
    }
    try {
        response = await fetch(url, {
            ...options,
            headers,
            credentials: 'include'
        });
    } catch (error) {
        throw new Error('The server could not be reached. Please try again.');
    }
    const body = await response.json().catch(() => ({}));
    const isAuthEndpoint = url.startsWith('/api/auth/');
    if (response.status === 401 && retryAfterRefresh && !isAuthEndpoint && await refreshAccessToken()) {
        return request(url, options, false);
    }
    if (!response.ok) throw new Error(getErrorMessage(response.status, body));
    return body;
}

async function loadPosts(url = API_URL) {
    currentRequestUrl = url;
    loadingState.classList.remove('hidden');
    postsList.classList.add('hidden');
    emptyState.classList.add('hidden');
    try {
        const response = await request(url);
        renderPosts(response.data);
    } catch (error) {
        renderPosts(null);
        showMessage(error.message, 'error');
    } finally {
        loadingState.classList.add('hidden');
        postsList.classList.remove('hidden');
    }
}

function searchUrl(page = 0) {
    const keyword = searchInput.value.trim();
    const params = new URLSearchParams({
        page: String(page),
        size: pageSize.value,
        sortBy: sortBy.value,
        direction: sortDirection.value
    });
    if (keyword) params.set('keyword', keyword);
    return `${keyword ? `${API_URL}/search` : API_URL}?${params}`;
}

function resetForm() {
    editingId = null;
    form.reset();
    updateCounter();
    submitButton.innerHTML = 'Create post <span aria-hidden="true">&#8594;</span>';
    cancelEdit.classList.add('hidden');
    formMode.textContent = 'New post';
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    const content = contentInput.value.trim();
    if (!content) {
        showMessage('Post content cannot be blank.', 'error');
        contentInput.focus();
        return;
    }
    if (content.length > MAX_CONTENT_LENGTH) {
        showMessage(`Post content must be ${MAX_CONTENT_LENGTH} characters or fewer.`, 'error');
        return;
    }

    submitButton.disabled = true;
    try {
        await request(editingId ? `${API_URL}/${editingId}` : API_URL, {
            method: editingId ? 'PUT' : 'POST',
            body: JSON.stringify({ content })
        });
        showMessage(editingId ? 'Post updated successfully.' : 'Post created successfully.', 'success');
        resetForm();
        await loadPosts(currentRequestUrl);
    } catch (error) {
        showMessage(error.message, 'error');
    } finally {
        submitButton.disabled = false;
    }
});

authForm.addEventListener('submit', async event => {
    event.preventDefault();
    const action = event.submitter?.dataset.authAction || 'login';
    const credentials = { username: authUsername.value.trim(), password: authPassword.value };
    try {
        if (action === 'register') {
            await request('/api/auth/register', {
                method: 'POST', body: JSON.stringify(credentials)
            }, false);
        }
        const response = await request('/api/auth/login', {
            method: 'POST', body: JSON.stringify(credentials)
        }, false);
        accessToken = response.data.accessToken;
        updateAuthState(response.data.user);
        authPassword.value = '';
        showMessage(action === 'register' ? 'Account created and signed in.' : 'Signed in.', 'success');
        await loadPosts(searchUrl());
    } catch (error) {
        showMessage(error.message, 'error');
    }
});

logoutButton.addEventListener('click', async () => {
    try {
        await request('/api/auth/logout', { method: 'POST' }, false);
        accessToken = null;
        updateAuthState(null);
        showMessage('Signed out. Existing access tokens remain valid until they expire.', 'success');
    } catch (error) {
        showMessage(error.message, 'error');
    }
});

postsList.addEventListener('click', async event => {
    const button = event.target.closest('button[data-id]');
    if (!button) return;
    const id = button.dataset.id;

    if (button.classList.contains('delete-button')) {
        if (!window.confirm('Delete this post permanently?')) return;
        button.disabled = true;
        try {
            await request(`${API_URL}/${id}`, { method: 'DELETE' });
            showMessage('Post deleted successfully.', 'success');
            await loadPosts(currentRequestUrl);
        } catch (error) {
            button.disabled = false;
            showMessage(error.message, 'error');
        }
        return;
    }

    if (button.classList.contains('edit-button')) {
        button.disabled = true;
        try {
            const response = await request(`${API_URL}/${id}`);
            editingId = id;
            contentInput.value = response.data?.content || '';
            updateCounter();
            submitButton.innerHTML = 'Save changes <span aria-hidden="true">&#8594;</span>';
            cancelEdit.classList.remove('hidden');
            formMode.textContent = 'Editing post';
            contentInput.focus();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } catch (error) {
            showMessage(error.message, 'error');
        } finally {
            button.disabled = false;
        }
    }
});

searchForm.addEventListener('submit', event => {
    event.preventDefault();
    loadPosts(searchUrl());
});

clearSearch.addEventListener('click', () => {
    searchInput.value = '';
    showMessage('Showing all posts.', 'success');
    loadPosts(searchUrl());
});

previousPage.addEventListener('click', () => {
    if (currentPage > 0) loadPosts(searchUrl(currentPage - 1));
});

nextPage.addEventListener('click', () => {
    if (currentPage < totalPages - 1) loadPosts(searchUrl(currentPage + 1));
});

cancelEdit.addEventListener('click', resetForm);
contentInput.addEventListener('input', updateCounter);

updateCounter();
refreshAccessToken().then(authenticated => {
    if (authenticated) loadPosts(searchUrl());
});
