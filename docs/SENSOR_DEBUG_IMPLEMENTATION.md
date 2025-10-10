# 센서 디버그 UI 구현 완료

> **날짜**: 2025-10-06  
> **상태**: ✅ 완료 및 빌드 성공

---

## 구현 내용

### 1. 권한 및 매니페스트 설정

#### `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />

<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="true" />
<uses-feature android:name="android.hardware.sensor.gyroscope" android:required="true" />
```

- **BODY_SENSORS**: 센서 데이터 접근 권한
- **HIGH_SAMPLING_RATE_SENSORS**: 고속 샘플링 (100Hz+) 권한
- 가속도계와 자이로스코프가 필수 하드웨어로 선언됨

---

## 2. 프로젝트 구조

```
app/src/main/java/com/bluemarlin/puttmeter/
├── domain/
│   └── model/
│       ├── Vector3.kt              # 3D 벡터 데이터 클래스
│       └── SensorData.kt           # 센서 데이터 모델
├── data/
│   └── sensor/
│       └── SensorDataSource.kt     # 센서 수집 구현
└── presentation/
    ├── MainActivity.kt             # 메인 액티비티 (권한 처리)
    ├── debug/
    │   ├── SensorDebugViewModel.kt          # ViewModel
    │   ├── SensorDebugViewModelFactory.kt   # Factory
    │   └── SensorDebugScreen.kt             # Compose UI
    └── theme/
        └── Theme.kt
```

---

## 3. 주요 컴포넌트

### 3.1 도메인 모델

#### `Vector3.kt`
```kotlin
data class Vector3(val x: Float, val y: Float, val z: Float) {
    fun magnitude(): Float                    // 벡터 크기
    fun normalized(): Vector3                 // 정규화
}
```

#### `SensorData.kt`
```kotlin
data class SensorData(
    val timestamp: Long,
    val acceleration: Vector3,                // m/s²
    val gyroscope: Vector3,                   // rad/s
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float
)

data class SensorStatus(
    val isAccelerometerAvailable: Boolean,
    val isGyroscopeAvailable: Boolean,
    val samplingRate: Int,
    val isCollecting: Boolean
)
```

### 3.2 데이터 레이어

#### `SensorDataSource.kt`
- **인터페이스**: `SensorDataSource`
- **구현체**: `AndroidSensorDataSource`
- **기능**:
  - `getSensorDataStream()`: Flow<SensorData> 반환
  - `getSensorStatus()`: 센서 가용성 확인
  - SensorManager 래핑
  - 실시간 센서 이벤트 → Flow 변환
  - 100Hz 샘플링 (10ms 간격)

#### 특징
```kotlin
override fun getSensorDataStream(samplingRate: Int): Flow<SensorData> = callbackFlow {
    // 가속도계 + 자이로스코프 동시 수집
    // 최소 10ms 간격으로 emit
    // awaitClose로 자동 리스너 해제
}
```

### 3.3 프레젠테이션 레이어

#### `SensorDebugViewModel.kt`
- **상태**: `SensorDebugUiState`
  - 센서 상태 (가용성, 수집 여부)
  - 현재 센서 데이터
  - 샘플 카운트
  - 평균 업데이트 속도 (Hz)
  - 오류 메시지

- **주요 메서드**:
  - `startCollecting()`: 센서 수집 시작
  - `stopCollecting()`: 센서 수집 중지
  - `clearError()`: 오류 메시지 제거

- **실시간 업데이트 속도 계산**:
  - 1초마다 샘플 수 카운트하여 평균 Hz 계산
  - 실제 샘플링 레이트 모니터링

#### `SensorDebugScreen.kt`
WearOS 최적화 Compose UI:

1. **센서 상태 카드**
   - 가속도계/자이로스코프 가용성 표시
   - 실시간 업데이트 속도 (Hz)
   - 수집 상태

2. **가속도계 데이터 카드**
   - X, Y, Z 축 값 (m/s²)
   - 벡터 크기 (magnitude)
   - 모노스페이스 폰트로 정렬된 숫자

3. **자이로스코프 데이터 카드**
   - X, Y, Z 축 각속도 (rad/s)
   - 벡터 크기

4. **움직임 인디케이터**
   - 시각적 프로그레스 바 (텍스트 기반)
   - 움직임 강도에 따라 색상 변화:
     - 초록: 낮은 움직임 (< 40%)
     - 노랑: 중간 움직임 (40-70%)
     - 빨강: 높은 움직임 (> 70%)

5. **제어 버튼**
   - "측정 시작" / "측정 중지"
   - 센서 가용성에 따라 활성화/비활성화

6. **샘플 카운터**
   - 총 수집된 샘플 수

7. **오류 표시**
   - 센서 오류 발생 시 Chip으로 표시
   - 클릭 시 제거

#### `MainActivity.kt`
- **권한 처리**:
  - `ActivityResultContracts.RequestMultiplePermissions` 사용
  - BODY_SENSORS + HIGH_SAMPLING_RATE_SENSORS 요청
  - 권한 없으면 "센서 권한이 필요합니다" 화면 표시

- **ViewModel 생성**:
  - `by viewModels { SensorDebugViewModelFactory(this) }` 패턴
  - Factory를 통해 SensorDataSource 주입

---

## 4. 동작 흐름

### 앱 시작
```
1. MainActivity.onCreate()
2. installSplashScreen()
3. checkPermissions() → 권한 요청
4. 권한 승인 시:
   → SensorDebugScreen 렌더링
   → ViewModel 초기화
   → getSensorStatus() 호출 → 센서 가용성 확인
```

### 측정 시작
```
1. 사용자가 "측정 시작" 버튼 클릭
2. viewModel.startCollecting() 호출
3. sensorDataSource.getSensorDataStream() Flow 수집 시작
4. SensorManager에 리스너 등록:
   - Accelerometer (SENSOR_DELAY_GAME)
   - Gyroscope (SENSOR_DELAY_GAME)
5. 센서 이벤트 발생:
   → onSensorChanged()
   → Vector3 생성
   → emitSensorData() (10ms 간격 체크)
   → Flow emit
   → ViewModel 상태 업데이트
   → UI 리컴포지션 (자동)
```

### 측정 중지
```
1. 사용자가 "측정 중지" 버튼 클릭
2. viewModel.stopCollecting()
3. Flow 수집 종료
4. awaitClose { } 블록 실행 → SensorManager.unregisterListener()
```

---

## 5. 빌드 결과

### 빌드 성공
```bash
> Task :app:assembleDebug
BUILD SUCCESSFUL in 15s
```

### APK 생성 위치
```
app/build/outputs/apk/debug/app-debug.apk
```

### 경고 (무시 가능)
```
ScalingLazyColumn is deprecated.
→ androidx.wear.compose.foundation.lazy 패키지로 이동됨
→ 기능은 정상 동작, 추후 업데이트 예정
```

---

## 6. 테스트 방법

### Galaxy Watch 실기기 테스트
1. **USB 디버깅 활성화**:
   - Galaxy Watch 설정 → 개발자 옵션 → ADB 디버깅 활성화
   - Galaxy Wearable 앱에서 "무선 디버깅" 활성화

2. **설치**:
   ```bash
   # ADB 연결 확인
   adb devices
   
   # APK 설치
   adb install app/build/outputs/apk/debug/app-debug.apk
   
   # 또는 Android Studio에서 직접 Run
   ```

3. **권한 승인**:
   - 앱 실행 시 "BODY_SENSORS" 권한 요청
   - "허용" 선택

4. **센서 테스트**:
   - "측정 시작" 버튼 클릭
   - 워치를 움직이면서 데이터 변화 확인:
     - 가속도계: 손목 움직임에 따라 X, Y, Z 값 변화
     - 자이로스코프: 회전 동작에 따라 각속도 변화
     - 움직임 인디케이터 바 색상 및 길이 변화
   - 업데이트 속도 (Hz) 확인: 약 90-100 Hz 예상

5. **정지 상태 테스트**:
   - 워치를 평평한 곳에 놓기
   - 가속도계 Z축 ≈ 9.8 m/s² (중력)
   - 자이로스코프 ≈ 0 rad/s

---

## 7. 센서 데이터 해석 가이드

### 가속도계 (Accelerometer)
- **단위**: m/s²
- **축 방향** (워치 화면 기준):
  - X축: 좌(-) → 우(+)
  - Y축: 하(-) → 상(+)
  - Z축: 화면 안쪽(-) → 바깥쪽(+)
- **중력 포함**: 정지 상태에서도 중력(9.8 m/s²) 감지
- **예시**:
  - 정지: (0, 0, 9.8) - Z축에 중력
  - 손목 흔들기: 큰 X, Y 변화
  - 퍼팅 모션: Z축 큰 변화 + X/Y 복합

### 자이로스코프 (Gyroscope)
- **단위**: rad/s (라디안/초)
- **축 의미**:
  - X축: Pitch (상하 회전)
  - Y축: Roll (좌우 기울기)
  - Z축: Yaw (좌우 회전)
- **정지 시**: (0, 0, 0)
- **예시**:
  - 손목 비틀기: Z축 변화
  - 팔 위아래: X축 변화

---

## 8. 다음 단계

### Phase 2 구현 예정 (스트로크 감지)
1. **신호 처리 추가**:
   - Butterworth Low-Pass Filter (20-30Hz)
   - 중력 보정 (Gravity Compensation)
   - Kalman Filter (옵션)

2. **스트로크 감지 알고리즘**:
   - 정지 상태 감지 (< 0.3 m/s²)
   - 백스윙 감지 (가속도 증가)
   - 임팩트 감지 (가속도 피크)
   - 팔로우스루 감지

3. **데이터 버퍼링**:
   - 최근 N개 샘플 저장 (예: 200개 = 2초)
   - 스트로크 전체 구간 분석

4. **거리 예측 모델**:
   - 임팩트 속도 계산
   - 임펄스 적분
   - 회귀 모델 적용

---

## 9. 알려진 이슈 및 해결책

### 이슈 1: ScalingLazyColumn Deprecated 경고
- **상태**: 경고만 표시, 기능 정상 동작
- **해결**: `androidx.wear.compose.foundation.lazy` 패키지 사용으로 업데이트 필요
- **우선순위**: 낮음 (추후 리팩토링)

### 이슈 2: 에뮬레이터에서 센서 미지원
- **원인**: Wear OS 에뮬레이터는 센서 시뮬레이션 제한적
- **해결**: 실기기 테스트 필수 (Galaxy Watch 7/8)

### 이슈 3: 배터리 소모
- **원인**: 고속 센서 샘플링 (100Hz)
- **해결**:
  - 측정 중이 아닐 때는 자동으로 센서 리스너 해제
  - awaitClose 블록으로 안전한 정리
  - 추후 배경 제한 추가 (예: 5분 자동 중지)

---

## 10. 참고 자료

### Android Sensor API
- [Sensor Overview](https://developer.android.com/guide/topics/sensors/sensors_overview)
- [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager)
- [SensorEvent](https://developer.android.com/reference/android/hardware/SensorEvent)

### Wear OS Compose
- [Compose for Wear OS](https://developer.android.com/training/wearables/compose)
- [ScalingLazyColumn](https://developer.android.com/reference/kotlin/androidx/wear/compose/material/package-summary#ScalingLazyColumn)

### Kotlin Coroutines & Flow
- [callbackFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html)
- [StateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)

---

## 요약

✅ **완료된 작업**:
- 센서 권한 및 매니페스트 설정
- 도메인 모델 (Vector3, SensorData)
- 센서 수집 레이어 (SensorDataSource)
- ViewModel (상태 관리, 업데이트 속도 계산)
- Compose 디버그 UI (실시간 센서 데이터 표시)
- 권한 처리 (MainActivity)
- 빌드 성공 및 APK 생성

🎯 **핵심 기능**:
- 가속도계 + 자이로스코프 **동시 수집** (100Hz)
- **실시간** UI 업데이트 (Flow + StateFlow)
- 센서 상태 모니터링
- 움직임 시각적 표현
- Clean Architecture 구조

🚀 **다음 목표**:
- 스트로크 감지 알고리즘 구현
- 신호 필터링 (노이즈 제거)
- 거리 예측 모델 개발
- 캘리브레이션 UI

---

**작성일**: 2025-10-06  
**빌드 환경**: Android SDK 36, Kotlin 2.1.0, Compose for Wear OS

