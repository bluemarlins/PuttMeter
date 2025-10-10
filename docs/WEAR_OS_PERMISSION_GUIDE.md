# Wear OS 권한 설정 가이드

## Galaxy Watch 7/8 권한 설정 방법

### 🚨 중요: Wear OS 권한 시스템의 특징

Wear OS (특히 Galaxy Watch)에서는 **워치 자체에서 권한을 설정할 수 없는 경우**가 많습니다.
대신 **페어링된 스마트폰의 Galaxy Wearable 앱**을 통해 권한을 관리합니다.

---

## 📱 방법 1: Galaxy Wearable 앱에서 권한 부여 (권장)

### 안드로이드 스마트폰에서:

1. **Galaxy Wearable 앱** 실행
2. 메뉴 또는 설정 아이콘 탭
3. **"워치 설정"** 또는 **"앱"** 선택
4. **"PuttMeter"** 앱 찾기
5. **"권한"** 선택
6. **"신체 센서"** 또는 **"Body sensors"** 토글을 **ON**
7. 워치에서 앱 재실행

### iOS (아이폰)에서:

1. **Galaxy Watch** 앱 실행
2. **"앱"** 탭
3. **"PuttMeter"** 선택
4. **"권한"** 설정
5. **"신체 센서"** 활성화

---

## ⌚ 방법 2: Galaxy Watch 자체에서 (일부 기기만 가능)

1. 워치 홈 화면에서 위에서 아래로 스와이프 (Quick Panel)
2. **설정 ⚙️** 아이콘 탭
3. **"앱"** 또는 **"Applications"** 선택
4. **"PuttMeter"** 찾기
5. **"권한"** 선택 (있는 경우)
6. **"신체 센서"** 활성화

**주의**: Galaxy Watch에서는 이 메뉴가 없거나 권한 항목이 비어있을 수 있습니다.

---

## 🔧 방법 3: ADB를 통한 권한 부여 (개발자용)

개발 중이거나 위 방법이 작동하지 않는 경우:

```bash
# 워치를 ADB로 연결
adb devices

# 권한 직접 부여
adb shell pm grant com.bluemarlin.puttmeter android.permission.BODY_SENSORS

# 확인
adb shell dumpsys package com.bluemarlin.puttmeter | grep permission
```

---

## ❓ 권한이 없어도 작동하는 경우

일부 Wear OS 기기에서는 **BODY_SENSORS 권한이 자동으로 부여**되거나, 센서 접근 시 런타임으로 묻지 않고 바로 허용될 수 있습니다.

### 확인 방법:
1. 앱에서 **"센서 디버그"** 메뉴 진입
2. **"측정 시작"** 버튼 클릭
3. 센서 값이 표시되면 권한이 부여된 것입니다

---

## 🐛 트러블슈팅

### 문제 1: "권한이 없습니다" 화면에서 벗어나지 못함

**해결책**:
- Galaxy Wearable 앱에서 권한 부여 후 **앱 강제 종료**
- 워치에서 앱 다시 실행

### 문제 2: Galaxy Wearable 앱에 PuttMeter가 보이지 않음

**해결책**:
- 워치에서 앱을 한 번 이상 실행한 후 Galaxy Wearable 앱 확인
- Galaxy Wearable 앱을 최신 버전으로 업데이트
- 워치와 폰 연결 해제 후 재연결

### 문제 3: 권한 메뉴가 비어있음

**해결책**:
- ADB를 통한 권한 부여 시도 (위 방법 3)
- 또는 앱을 그대로 실행 (자동으로 권한이 부여될 수 있음)

---

## 📊 로그 확인

권한 상태를 확인하려면:

```bash
adb logcat -s PuttMeter
```

**정상 로그**:
```
D/PuttMeter: BODY_SENSORS permission: true
```

**권한 없음**:
```
D/PuttMeter: BODY_SENSORS permission: false
```

---

## 🔄 앱 코드 개선 (자동 권한 우회)

현재 구현에서는 권한 체크를 먼저 하지만, Wear OS에서는 **센서 접근 시도 → 실패 시 안내** 방식이 더 효과적일 수 있습니다.

다음 업데이트에서 이 방식으로 변경 예정입니다.

