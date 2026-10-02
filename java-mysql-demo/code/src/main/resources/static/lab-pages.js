document.getElementById('logoutButton')?.addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        const response = await fetch('/api/logout', {method: 'POST'});
        const result = await response.json();
        if (!response.ok || result.status !== 'success') throw new Error(result.message || 'Không thể đăng xuất.');
        window.location.assign('/');
    } catch (error) {
        document.getElementById('feedback').textContent = error.message;
        button.disabled = false;
    }
});
