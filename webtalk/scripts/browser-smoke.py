from playwright.sync_api import sync_playwright, expect
import argparse
import uuid
from pathlib import Path
from shutil import which

parser = argparse.ArgumentParser(description='독립 테스트 서버에서 WebTalk 브라우저 회귀 테스트를 실행합니다.')
parser.add_argument('--base-url', default='http://localhost:18080')
parser.add_argument('--chrome', help='설치된 Chrome 실행 파일 경로. 생략하면 Playwright Chromium을 사용합니다.')
parser.add_argument('--screenshots-dir', help='선택: 데스크톱/모바일 실행 화면을 저장할 폴더')
args = parser.parse_args()

BASE = args.base_url.rstrip('/')
errors = []
dialogs = []
chrome = args.chrome
if not chrome:
    chrome = which('google-chrome') or which('chromium')
    installed_mac_chrome = Path('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome')
    if not chrome and installed_mac_chrome.exists():
        chrome = str(installed_mac_chrome)
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, headless=True)
    a = browser.new_context(viewport={'width':1280, 'height':850})
    b = browser.new_context(viewport={'width':390, 'height':844})
    def account(context, prefix):
        login = prefix + uuid.uuid4().hex[:8]
        response = context.request.post(BASE+'/api/auth/signup', data={'loginId':login,'nickname':prefix,'password':'test-password'})
        assert response.status == 201, response.text()
        user = response.json()
        assert context.request.post(BASE+'/api/auth/login', data={'loginId':login,'password':'test-password'}).status == 200
        return user
    alice = account(a, 'Alice')
    bob = account(b, 'Bob')
    page_a, page_b = a.new_page(), b.new_page()
    for page in [page_a,page_b]:
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('dialog', lambda dialog: (dialogs.append(dialog.message), dialog.dismiss()))
    # 일반 HTTP 접속에서 사용하는 UUID 대체 경로도 검사한다.
    page_a.add_init_script("Object.defineProperty(Crypto.prototype, 'randomUUID', {value:undefined, configurable:true})")
    links = []
    blocked = [False]
    def route(ws):
        if blocked[0]:
            ws.close()
        else:
            ws.connect_to_server()
            links.append(ws)
    page_a.route_web_socket('**/ws', route)
    for page in [page_a,page_b]:
        page.goto(BASE+'/chat.html')
        page.wait_for_function('window.WebTalkSocket && WebTalkSocket.isConnected()', timeout=20000)
    response = b.request.post(BASE+'/api/chat-rooms/direct',data={'targetUserId':alice['userId']})
    assert response.status == 200, response.text()
    room = response.json()['roomId']
    expect(page_a.locator('#roomList li[data-room-id]')).to_have_count(1, timeout=4000)
    expect(page_b.locator('#roomList li[data-room-id]')).to_have_count(1, timeout=4000)
    print('PASS: real-time new-room notifications', flush=True)
    for page in [page_a,page_b]:
        page.locator('#roomList li[data-room-id]').click()
        expect(page.locator('#connectionStatus')).to_have_text('연결됨')
    page_b.locator('#messageInput').fill('안녕하세요\n반가워요')
    page_b.locator('#sendButton').click()
    expect(page_a.locator('.bubble .content')).to_have_text(['안녕하세요\n반가워요'])
    page_a.locator('#messageInput').fill('한글 조합')
    page_a.locator('#messageInput').evaluate("el => el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter',isComposing:true,bubbles:true}))")
    expect(page_a.locator('#messageInput')).to_have_value('한글 조합')
    page_a.locator('#messageInput').press('Enter')
    expect(page_b.locator('.bubble-row')).to_have_count(2)
    print('PASS: bidirectional messages, multiline content and Korean composition',flush=True)
    # 신규 메시지 수십 개를 보내 이전 기록 조회를 확인한다.
    page_b.evaluate('(room) => { for(let i=0;i<60;i++) WebTalkSocket.publish(room,crypto.randomUUID(),`기록 ${i}`); }',room)
    expect(page_b.locator('.bubble-row')).to_have_count(62,timeout=15000)
    expect(page_a.locator('.bubble-row')).to_have_count(62,timeout=15000)
    page_a.reload()
    page_a.wait_for_function('window.WebTalkSocket && WebTalkSocket.isConnected()')
    page_a.locator('#roomList li[data-room-id]').click()
    expect(page_a.locator('.bubble-row')).to_have_count(50)
    page_a.locator('#olderButton').click()
    expect(page_a.locator('.bubble-row')).to_have_count(62)
    expect(page_a.locator('#olderButton')).to_be_hidden()
    print('PASS: older message pagination',flush=True)
    # 재연결을 잠시 차단하고 50개보다 많은 메시지를 쌓는다.
    blocked[0] = True
    links[-1].close()
    page_b.evaluate('(room) => { for(let i=0;i<65;i++) WebTalkSocket.publish(room,crypto.randomUUID(),`복구 ${i}`); }',room)
    expect(page_b.locator('.bubble-row')).to_have_count(127, timeout=15000)
    blocked[0] = False
    if '연결 끊김' in page_a.locator('#connectionStatus').inner_text():
        page_a.locator('#connectionStatus').click()
    expect(page_a.locator('#connectionStatus')).to_have_text('연결됨',timeout=20000)
    expect(page_a.locator('.bubble-row')).to_have_count(127,timeout=20000)
    ids = page_a.locator('.bubble-row').evaluate_all('(els) => els.map(el => Number(el.dataset.messageId))')
    assert len(ids) == len(set(ids)) and ids == sorted(ids)
    print('PASS: reconnect restores 65 messages without duplicates, in server order',flush=True)
    # 다른 방에 있는 동안 안 읽은 수가 실시간 갱신된다.
    page_a.set_viewport_size({'width':390,'height':844})
    expect(page_a.locator('#backButton')).to_be_visible()
    page_a.locator('#backButton').click()
    expect(page_a.locator('#chatRoomView')).to_be_hidden()
    page_b.locator('#messageInput').fill('안 읽은 메시지')
    page_b.locator('#sendButton').click()
    expect(page_a.locator('.unread-badge')).to_have_text('1',timeout=4000)
    page_a.locator('#roomList li[data-room-id]').click()
    expect(page_a.locator('.unread-badge')).to_have_count(0,timeout=4000)
    overflow = page_b.evaluate('document.documentElement.scrollWidth > innerWidth')
    assert not overflow, 'mobile horizontal overflow'
    print('PASS: mobile back button, unread notifications and read acknowledgement',flush=True)
    # REST API가 실제 커서 인자를 처리하는지도 확인한다.
    history = a.request.get(f'{BASE}/api/chat-rooms/{room}/messages?after=0')
    assert history.status == 200 and len(history.json()) == 50
    assert a.request.get(f'{BASE}/api/chat-rooms/{room}/messages?before=1&after=0').status == 400
    assert a.request.get(f'{BASE}/api/chat-rooms/{room}/messages?before=invalid').status == 400
    if args.screenshots_dir:
        output = Path(args.screenshots_dir)
        output.mkdir(parents=True, exist_ok=True)
        page_a.set_viewport_size({'width':1280,'height':850})
        page_a.screenshot(path=str(output/'chat-desktop.png'))
        page_b.screenshot(path=str(output/'chat-mobile.png'))
    # 세 번째 상대와 검색/방별 초안/실패 메시지 복원을 점검한다.
    page_a.set_viewport_size({'width':1280, 'height':850})
    c = browser.new_context()
    charlie = account(c, 'Charlie')
    page_a.locator('#messageInput').fill('Bob에게만 보낼 초안')
    page_a.locator('#userSearchInput').fill(charlie['loginId'])
    page_a.locator('#userSearchInput').press('Enter')
    expect(page_a.locator('#searchResults li')).to_have_count(1)
    page_a.locator('#searchResults li').press('Enter')
    expect(page_a.locator('#peerNameLabel')).to_have_text('Charlie')
    expect(page_a.locator('#messageInput')).to_have_value('')
    page_a.locator('#messageInput').fill('Charlie에게만 보낼 초안')
    other = next(r['roomId'] for r in a.request.get(BASE+'/api/chat-rooms').json() if r['peer']['userId'] == charlie['userId'])
    page_a.locator(f'#roomList li[data-room-id="{room}"]').click()
    expect(page_a.locator('#messageInput')).to_have_value('Bob에게만 보낼 초안')
    page_a.locator(f'#roomList li[data-room-id="{other}"]').click()
    expect(page_a.locator('#messageInput')).to_have_value('Charlie에게만 보낼 초안')
    page_a.locator(f'#roomList li[data-room-id="{room}"]').click()
    page_a.locator('#messageInput').fill('')
    print('PASS: keyboard search and separate drafts for each recipient', flush=True)

    blocked[0] = True
    links[-1].close()
    page_a.wait_for_function('!WebTalkSocket.isConnected()')
    retry_text = '연결 복구 후 재전송할 메시지'
    page_a.locator('#messageInput').fill(retry_text)
    page_a.locator('#sendButton').click()
    expect(page_a.locator('.retry-button')).to_have_count(1)
    page_a.locator(f'#roomList li[data-room-id="{other}"]').click()
    expect(page_a.locator('.retry-button')).to_have_count(0)
    page_a.locator(f'#roomList li[data-room-id="{room}"]').click()
    expect(page_a.locator('.retry-button')).to_have_count(1)
    blocked[0] = False
    page_a.evaluate('WebTalkSocket.retryNow()')
    expect(page_a.locator('#connectionStatus')).to_have_text('연결됨', timeout=20000)
    page_a.locator('.retry-button').click()
    retried = page_a.locator('.bubble-row.mine').filter(has=page_a.get_by_text(retry_text, exact=True))
    expect(retried.locator('.state-sent')).to_have_count(1)
    expect(retried).to_have_count(1)
    print('PASS: failed send survives room changes and retry sends once', flush=True)

    # 과거 대화를 읽는 동안 수신한 새 메시지는 최신으로 이동하기 전까지 안 읽음이다.
    expect(page_a.locator('.unread-badge')).to_have_count(0)
    page_a.locator('#messageList').evaluate('el => el.scrollTop = 0')
    page_b.locator('#messageInput').fill('화면 아래에서 기다리는 새 메시지')
    page_b.locator('#sendButton').click()
    expect(page_a.locator('#latestMessagesButton')).to_be_visible()
    expect(page_a.locator('.unread-badge')).to_have_text('1')
    assert page_a.locator('#messageList').evaluate('el => el.scrollTop') == 0
    if args.screenshots_dir:
        page_a.screenshot(path=str(Path(args.screenshots_dir)/'chat-unread.png'))
    page_a.locator('#latestMessagesButton').click()
    expect(page_a.locator('.unread-badge')).to_have_count(0)
    print('PASS: reading older history preserves scroll and unread state', flush=True)

    # 기록 API 실패를 한 번 주입한 뒤 재시도 버튼으로 복구한다.
    page_a.locator(f'#roomList li[data-room-id="{other}"]').click()
    failures = [1]
    def fail_history_once(route):
        if failures[0]:
            failures[0] -= 1
            route.fulfill(status=503, json={'code':'TEST_FAILURE', 'message':'테스트용 일시 장애'})
        else:
            route.continue_()
    history_pattern = f'**/api/chat-rooms/{room}/messages'
    page_a.route(history_pattern, fail_history_once)
    page_a.locator(f'#roomList li[data-room-id="{room}"]').click()
    expect(page_a.locator('#historyStatus')).to_contain_text('불러오지 못했습니다')
    if args.screenshots_dir:
        page_a.screenshot(path=str(Path(args.screenshots_dir)/'chat-history-retry.png'))
    page_a.locator('#olderButton').click()
    expect(page_a.locator('.bubble-row')).to_have_count(50)
    expect(page_a.locator('#olderButton')).to_have_text('이전 메시지 불러오기')
    page_a.unroute(history_pattern, fail_history_once)
    print('PASS: history request failure and user-triggered recovery', flush=True)

    # 말투 설정창의 임시 편집, 저장·삭제와 AI 장애/추천 UI를 확인한다.
    page_a.locator('#styleButton').click()
    page_a.locator('#styleAnswer0').fill('잠시 닫아도 남아야 하는 편집')
    page_a.locator('#styleDialog').press('Escape')
    page_a.locator('#styleButton').click()
    expect(page_a.locator('#styleAnswer0')).to_have_value('잠시 닫아도 남아야 하는 편집')
    for i in range(6):
        page_a.locator(f'#styleAnswer{i}').fill('좋아요')
    for i in range(3):
        page_a.locator(f'#styleRewrite{i}').fill('좋아요')
    page_a.locator('#styleForm button[type=submit]').click()
    expect(page_a.locator('#styleStatus')).to_contain_text('저장했습니다')
    page_a.locator('#closeStyleButton').click()
    suggestions_pattern = f'**/api/chat-rooms/{room}/reply-suggestions'
    def unavailable(route):
        route.fulfill(status=503, json={'code':'AI_UNAVAILABLE', 'message':'AI 서버에 연결할 수 없습니다.'})
    page_a.route(suggestions_pattern, unavailable)
    page_a.locator('#suggestButton').click()
    expect(page_a.locator('#suggestStatus')).to_have_text('AI 서버에 연결할 수 없습니다.')
    expect(page_a.locator('#suggestButton')).to_be_enabled()
    page_a.unroute(suggestions_pattern, unavailable)
    def suggested(route):
        route.fulfill(status=200, json={'suggestions':['확인했어요']})
    page_a.route(suggestions_pattern, suggested)
    page_a.locator('#suggestButton').click()
    page_a.locator('#suggestions button').click()
    expect(page_a.locator('#messageInput')).to_have_value('확인했어요')
    # 후보 선택만으로 메시지가 자동 전송되지 않아야 한다.
    expect(page_b.get_by_text('확인했어요', exact=True)).to_have_count(0)
    page_a.unroute(suggestions_pattern, suggested)
    page_a.locator('#styleButton').click()
    page_a.locator('#deleteStyleButton').click()
    expect(page_a.locator('#styleStatus')).to_contain_text('삭제했습니다')
    page_a.locator('#closeStyleButton').click()
    print('PASS: survey edit/save/delete and simulated AI failure/suggestion selection', flush=True)

    # 다른 탭에서 로그아웃된 세션도 로그인 화면으로 안내한다.
    assert a.request.post(BASE+'/api/auth/logout',data={}).status == 204
    page_a.locator('#userSearchInput').fill('Bob')
    page_a.locator('#userSearchButton').click()
    page_a.wait_for_url('**/login.html?expired=1')
    expect(page_a.locator('#errorBanner')).to_contain_text('만료되었습니다')
    print('PASS: expired session redirects to login with an explanation', flush=True)
    page_a.locator('#loginId').fill(alice['loginId'])
    page_a.locator('#password').fill('wrong-password')
    page_a.locator('#loginButton').click()
    expect(page_a.locator('#errorBanner')).to_contain_text('올바르지 않습니다')
    expect(page_a.locator('#password')).to_have_value('')
    page_a.locator('#password').fill('test-password')
    page_a.locator('#loginButton').click()
    page_a.wait_for_url('**/chat.html')
    expect(page_a.locator('#currentUserLabel')).to_contain_text(alice['loginId'])
    page_a.locator('#logoutButton').click()
    page_a.wait_for_url('**/login.html')
    assert a.request.get(BASE+'/api/me').status == 401
    print('PASS: failed login, successful login and logout through the UI', flush=True)

    assert not dialogs, f'Unexpected dialogs: {dialogs}'
    assert not errors, errors
    print('PASS: no browser JavaScript errors',flush=True)
    browser.close()
