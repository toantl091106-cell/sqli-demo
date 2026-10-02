'use strict';

let previousUsersState = new Map();
let previousPostsState = new Map();
let usersLoaded = false;
let postsLoaded = false;
let mutationInFlight = false;
let databaseRequest = null;
let sessionRequest = null;

const scenarios = {
    '1': {
        title: 'KỊCH BẢN 1: VƯỢT KIỂM TRA ĐĂNG NHẬP',
        inputs: ['Tên đăng nhập', 'Mật khẩu (tài khoản mẫu trong lab)'],
        description: 'Chèn điều kiện hoặc dấu comment vào truy vấn nối chuỗi để bỏ qua kiểm tra mật khẩu. Nếu truy vấn tìm được tài khoản, máy chủ tạo phiên thật; kiểm chứng bằng trang tài khoản và quản trị.',
        example: "admin' #",
        normal: ['admin', 'admin123'],
        payload: ["admin' #", '123']
    },
    '2': {
        title: 'KỊCH BẢN 2: TRUY XUẤT DỮ LIỆU BẰNG UNION',
        inputs: ['Từ khóa tìm kiếm bài viết'],
        description: 'Truy vấn bình thường trả bài viết. UNION có thể đưa username và password từ users vào các cột title và content. Xem các dòng thực tế bên dưới để đánh giá kết quả.',
        example: "a' UNION SELECT id, username, password FROM users #",
        normal: ['Spring'],
        payload: ["a' UNION SELECT id, username, password FROM users #"]
    },
    '3': {
        title: 'KỊCH BẢN 3: CHÈN TÀI KHOẢN',
        inputs: ['Tên đăng nhập mới', 'Mật khẩu mới (dữ liệu mẫu)', 'Email'],
        description: 'Thao tác bình thường tạo tài khoản với role=user. Payload sửa cấu trúc INSERT để yêu cầu role=admin. Đối chiếu quyền thực tế của tài khoản vừa được tạo, thay vì suy luận từ chế độ truy vấn.',
        example: "hacker@gmail.com', 'admin') #",
        normal: ['demo_user', 'pass123', 'demo@example.test'],
        payload: ['hacker', '123456', "hacker@gmail.com', 'admin') #"]
    },
    '4': {
        title: 'KỊCH BẢN 4: CẬP NHẬT EMAIL / QUYỀN',
        inputs: ['Email mới cho tài khoản ID = 2'],
        description: 'Thao tác dự kiến chỉ sửa email của tài khoản ID=2. Khi nối chuỗi SQL, đầu vào có thể thêm phép gán role=admin. Kết quả cho biết tài khoản nào thực sự thay đổi, cùng email và quyền sau thao tác.',
        example: "hacker@gmail.com', role='admin",
        normal: ['user1.new@example.test'],
        payload: ["hacker@gmail.com', role='admin"]
    },
    '5': {
        title: 'KỊCH BẢN 5: XÓA BÀI VIẾT',
        inputs: ['ID bài viết cần xóa, ví dụ 1'],
        description: 'Hai chế độ cùng xóa bài viết theo ID trong posts. Với truy vấn nối chuỗi, OR 1=1 có thể làm điều kiện đúng cho mọi bài viết. Chế độ tham số hóa kiểm tra ID là số nguyên dương trước khi chạy. Bảng users không thuộc thao tác xóa này.',
        example: '1 OR 1=1',
        normal: ['1'],
        payload: ['1 OR 1=1']
    }
};

const outcomes = {
    authenticated: 'Đã tạo phiên đăng nhập',
    authentication_failed: 'Đăng nhập thất bại',
    results_found: 'Có dữ liệu truy vấn',
    no_results: 'Không có kết quả',
    inserted: 'Đã thêm dữ liệu',
    updated: 'Dữ liệu đã thay đổi',
    deleted: 'Đã xóa bài viết',
    no_change: 'Không có thay đổi',
    invalid_input: 'Đầu vào bị từ chối',
    sql_error: 'Truy vấn thất bại',
    transaction_error: 'Không hoàn tất giao dịch CSDL'
};

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = String(text ?? '');
    return node;
}

function modeName(mode) {
    return mode === 'prepared' ? 'Tham số hóa (PreparedStatement)'
        : mode === 'vulnerable' ? 'Nối chuỗi SQL (có lỗ hổng)' : 'Chưa xác định';
}

async function requestJson(url, options = {}) {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), options.method === 'POST' ? 30000 : 10000);
    try {
        const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', ...options, signal: controller.signal });
        let data;
        try {
            data = await response.json();
        } catch (error) {
            throw new Error('Máy chủ không trả dữ liệu JSON hợp lệ (HTTP ' + response.status + ').');
        }
        if (!response.ok) throw new Error(data.message || data.error || ('Yêu cầu thất bại (HTTP ' + response.status + ').'));
        if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('Phản hồi máy chủ không đúng định dạng.');
        return data;
    } catch (error) {
        if (error.name === 'AbortError') throw new Error('Hết thời gian chờ phản hồi máy chủ.');
        throw error;
    } finally {
        window.clearTimeout(timeout);
    }
}

function setBusy(busy) {
    mutationInFlight = busy;
    document.querySelectorAll('[data-mutation-control]').forEach(control => { control.disabled = busy; });
    document.getElementById('executeButton').setAttribute('aria-busy', String(busy));
}

function showLog(message, className = 'text-info') {
    document.getElementById('sqlLog').replaceChildren(element('div', className + ' result-sql', message));
}

function mutationError(error) {
    showLog('Không xác nhận được kết quả thao tác: ' + error.message
        + ' Nếu yêu cầu đã được gửi, thao tác có thể đã chạy trên máy chủ. Kiểm tra dữ liệu và phiên trước khi thử lại.', 'text-warning');
}

async function loadData(force = false) {
    if (databaseRequest) {
        await databaseRequest;
        if (!force) return;
    }
    databaseRequest = (async () => {
        const status = document.getElementById('dataStatus');
        const badge = document.getElementById('serverBadge');
        try {
            const data = await requestJson('/api/data');
            if (data.error) throw new Error(String(data.error));
            if (!Array.isArray(data.users) || !Array.isArray(data.posts)) throw new Error('Thiếu dữ liệu bảng trong phản hồi máy chủ.');
            renderUsersTable(data.users);
            renderPostsTable(data.posts);
            status.className = 'small text-success mb-3';
            status.textContent = 'Đã đồng bộ ' + data.users.length + ' tài khoản và ' + data.posts.length
                + ' bài viết lúc ' + new Date().toLocaleTimeString('vi-VN') + '.';
            badge.className = 'badge bg-dark border border-success text-success px-3 py-2 code-font';
            badge.textContent = 'Web và CSDL phản hồi';
        } catch (error) {
            status.className = 'small text-warning mb-3';
            status.textContent = 'Không đồng bộ được dữ liệu: ' + error.message
                + ' Các bảng đang giữ lần tải thành công gần nhất; chưa thể kết luận dữ liệu bị xóa.';
            badge.className = 'badge bg-dark border border-warning text-warning px-3 py-2 code-font';
            badge.textContent = 'Chưa đồng bộ được CSDL';
        }
    })();
    try { await databaseRequest; } finally { databaseRequest = null; }
}

function renderUsersTable(users) {
    previousUsersState = renderTable(document.getElementById('usersTable'), users,
        ['id', 'username', 'email', 'role'], previousUsersState, usersLoaded, 'Bảng users hiện không có bản ghi.', true);
    usersLoaded = true;
}

function renderPostsTable(posts) {
    previousPostsState = renderTable(document.getElementById('postsTable'), posts,
        ['id', 'title', 'content'], previousPostsState, postsLoaded, 'Bảng posts hiện không có bản ghi.', false);
    postsLoaded = true;
}

function renderTable(tbody, rows, columns, previous, loaded, emptyMessage, userTable) {
    const fragment = document.createDocumentFragment();
    const current = new Map();
    if (rows.length === 0) {
        const row = element('tr');
        const cell = element('td', 'text-center text-muted py-3', emptyMessage);
        cell.colSpan = columns.length;
        row.append(cell);
        fragment.append(row);
    }
    rows.forEach(record => {
        const key = String(record.id);
        const snapshot = JSON.stringify(record);
        current.set(key, snapshot);
        const row = element('tr');
        if (loaded && !previous.has(key)) row.className = 'row-added';
        else if (previous.has(key) && previous.get(key) !== snapshot) row.className = 'row-updated';
        columns.forEach(column => {
            const cell = element('td', column === 'id' ? 'code-font' : 'text-muted');
            if (userTable && column === 'role') {
                cell.append(element('span', 'badge ' + (record.role === 'admin' ? 'bg-danger' : 'bg-secondary'), record.role));
            } else {
                cell.textContent = String(record[column] ?? '');
                if (column === 'username') cell.className = 'fw-semibold text-white';
                if (column === 'title') cell.className = 'fw-semibold text-info';
            }
            row.append(cell);
        });
        fragment.append(row);
    });
    tbody.replaceChildren(fragment);
    return current;
}

async function loadSession(force = false) {
    if (sessionRequest) {
        await sessionRequest;
        if (!force) return;
    }
    sessionRequest = (async () => {
        const status = document.getElementById('sessionStatus');
        const details = document.getElementById('sessionDetails');
        const button = document.getElementById('logoutButton');
        try {
            const data = await requestJson('/api/session');
            if (typeof data.authenticated !== 'boolean') throw new Error('Thiếu trạng thái phiên trong phản hồi.');
            button.hidden = !data.authenticated;
            if (!data.authenticated) {
                status.className = 'small text-muted';
                status.textContent = 'Chưa đăng nhập. Trang tài khoản yêu cầu phiên; trang quản trị yêu cầu thêm quyền admin.';
                details.replaceChildren();
                return;
            }
            if (!data.user || typeof data.user !== 'object') throw new Error('Thiếu thông tin tài khoản của phiên.');
            status.className = 'small text-success';
            status.textContent = 'Đang đăng nhập: ' + String(data.user.username ?? '') + ' · Quyền hiện tại: ' + String(data.user.role ?? '');
            details.replaceChildren(element('div', '', 'Chế độ lúc đăng nhập: ' + modeName(data.mode)),
                element('div', data.bypassDetected ? 'text-warning mt-1' : 'mt-1', data.bypassDetected
                    ? 'Máy chủ ghi nhận thông tin nhập không khớp tài khoản nhưng truy vấn vẫn đăng nhập được.'
                    : 'Thông tin đăng nhập khớp tài khoản được máy chủ trả về.'));
        } catch (error) {
            status.className = 'small text-warning';
            status.textContent = 'Chưa xác nhận được phiên hiện tại: ' + error.message;
            details.replaceChildren();
            button.hidden = false;
        }
    })();
    try { await sessionRequest; } finally { sessionRequest = null; }
}

function changeScenario() {
    const scenario = scenarios[document.getElementById('scenario').value];
    const inputs = document.getElementById('formInputs');
    const sheet = document.getElementById('cheatSheetContent');
    inputs.replaceChildren();
    if (!scenario) { sheet.replaceChildren(); return; }
    scenario.inputs.forEach((description, index) => {
        const wrapper = element('div');
        const input = element('input', 'form-control');
        input.id = 'input' + (index + 1);
        input.type = 'text';
        input.autocomplete = 'off';
        input.spellcheck = false;
        input.placeholder = description;
        input.dataset.mutationControl = '';
        input.disabled = mutationInFlight;
        const label = element('label', 'small text-muted mb-1', description);
        label.htmlFor = input.id;
        wrapper.append(label, input);
        inputs.append(wrapper);
    });
    const example = element('p');
    example.append(element('b', '', 'Payload mẫu: '), element('code', '', scenario.example));
    sheet.replaceChildren(element('p', 'text-info fw-bold', scenario.title), element('p', '', scenario.description), example);
}

function fillInputs(values) {
    if (mutationInFlight) return;
    values.forEach((value, index) => {
        const input = document.getElementById('input' + (index + 1));
        if (input) input.value = value;
    });
}

function fillPayload() { fillInputs(scenarios[document.getElementById('scenario').value]?.payload || []); }
function fillNormalInput() { fillInputs(scenarios[document.getElementById('scenario').value]?.normal || []); }

function renderResult(result) {
    const log = document.getElementById('sqlLog');
    const color = result.status === 'error' ? 'text-danger' : result.status === 'rejected' ? 'text-warning' : 'text-info';
    const outcome = outcomes[result.outcome] || result.outcome || 'Đã nhận phản hồi';
    const fragment = document.createDocumentFragment();
    fragment.append(element('div', 'text-muted mb-2', '[Chế độ truy vấn] ' + modeName(result.mode)),
        element('div', color + ' fw-bold mb-2', '[Kết quả] ' + outcome),
        element('div', 'result-sql text-light mb-2', '[Câu SQL] ' + (result.sql || '(chưa có câu SQL)')),
        element('div', color + ' result-sql mb-2', '[Phản hồi máy chủ] ' + (result.message || 'Không có thông báo.')));
    const metrics = element('div', 'd-flex flex-wrap gap-2 mb-2');
    const labels = { rowCount: 'Dòng truy vấn trả về', affectedRows: 'Dòng thực sự thêm / thay đổi / xóa', matchedRows: 'Dòng MySQL báo ảnh hưởng', postsBefore: 'Bài viết trước', postsAfter: 'Bài viết sau' };
    Object.entries(labels).forEach(([key, label]) => {
        if (typeof result[key] === 'number') metrics.append(element('span', 'badge bg-dark border border-secondary text-light', label + ': ' + result[key]));
    });
    if (metrics.childNodes.length) fragment.append(metrics);
    if (result.user) fragment.append(element('div', 'text-success mb-2', 'Tài khoản được trả về: ' + String(result.user.username ?? '') + ' · Quyền: ' + String(result.user.role ?? '')));
    if (Array.isArray(result.rows)) {
        const title = element('div', 'text-muted fw-bold mt-3 mb-2', 'DỮ LIỆU KẾT QUẢ (' + result.rows.length + ' dòng)');
        fragment.append(title);
        if (result.rows.length === 0) fragment.append(element('div', 'text-muted', 'Truy vấn không trả về dòng dữ liệu.'));
        else fragment.append(resultTable(result.rows));
    }
    log.replaceChildren(fragment);
}

function resultTable(rows) {
    const wrapper = element('div', 'table-responsive border border-secondary border-opacity-25 rounded-2');
    const table = element('table', 'table table-cyber mb-0 align-middle');
    const columns = [...new Set(rows.flatMap(row => Object.keys(row)))];
    const head = element('thead');
    const heading = element('tr');
    columns.forEach(column => heading.append(element('th', '', column)));
    head.append(heading);
    const body = element('tbody');
    rows.forEach(record => {
        const row = element('tr');
        columns.forEach(column => row.append(element('td', 'text-light', record[column])));
        body.append(row);
    });
    table.append(head, body);
    wrapper.append(table);
    return wrapper;
}

async function executeAttack() {
    if (mutationInFlight) return;
    setBusy(true);
    const start = performance.now();
    const payload = { scenario: document.getElementById('scenario').value,
        secure: document.getElementById('secureMode').checked ? 'true' : 'false',
        input1: document.getElementById('input1')?.value || '',
        input2: document.getElementById('input2')?.value || '',
        input3: document.getElementById('input3')?.value || '' };
    showLog('Đang chờ kết quả từ máy chủ…', 'text-muted');
    try {
        const result = await requestJson('/api/execute', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        if (!['success', 'rejected', 'error'].includes(result.status)) throw new Error('Thiếu trạng thái thực thi trong phản hồi.');
        renderResult(result);
    } catch (error) {
        mutationError(error);
    } finally {
        document.getElementById('latencyText').textContent = 'Thời gian phản hồi: ' + Math.round(performance.now() - start) + ' ms';
        await Promise.allSettled([loadData(true), loadSession(true)]);
        setBusy(false);
    }
}

async function resetDatabase() {
    if (mutationInFlight) return;
    setBusy(true);
    showLog('Đang yêu cầu khôi phục dữ liệu mẫu…', 'text-muted');
    try {
        const result = await requestJson('/api/reset', { method: 'POST' });
        if (!['success', 'error'].includes(result.status)) throw new Error('Thiếu trạng thái khôi phục trong phản hồi.');
        showLog(result.message || (result.status === 'success' ? 'Máy chủ xác nhận khôi phục thành công.' : 'Máy chủ báo khôi phục thất bại.'), result.status === 'success' ? 'text-success' : 'text-danger');
    } catch (error) {
        mutationError(error);
    } finally {
        await Promise.allSettled([loadData(true), loadSession(true)]);
        setBusy(false);
    }
}

async function logout() {
    if (mutationInFlight) return;
    setBusy(true);
    try {
        const result = await requestJson('/api/logout', { method: 'POST' });
        if (result.status !== 'success') throw new Error(result.message || 'Máy chủ chưa xác nhận đăng xuất.');
        showLog(result.message || 'Máy chủ xác nhận đã đăng xuất.', 'text-info');
    } catch (error) {
        mutationError(error);
    } finally {
        await loadSession(true);
        setBusy(false);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    changeScenario();
    loadData();
    loadSession();
    document.getElementById('formInputs').addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.isComposing && !mutationInFlight) {
            event.preventDefault();
            executeAttack();
        }
    });
    window.setInterval(() => {
        if (!document.hidden && !mutationInFlight) { loadData(); loadSession(); }
    }, 3000);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && !mutationInFlight) { loadData(); loadSession(); }
    });
});
