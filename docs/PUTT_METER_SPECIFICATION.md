# PuttMeter - 골프 퍼팅 거리 측정 앱 기능 명세서

> **목적**: Galaxy Watch (7, 8)의 센서를 활용하여 퍼팅 스트로크의 힘과 예상 거리를 측정하는 WearOS 앱

---

## 1. 개요

### 1.1 앱 컨셉
- 사용자가 퍼팅 연습 시 스윙의 힘, 속도, 가속도를 측정하여 예상 퍼팅 거리를 실시간으로 제공
- 평평한 연습 그린에서 캘리브레이션 후, 실전/연습 시 거리 예측 제공
- 퍼팅 데이터 히스토리 및 일관성 분석 (템포, 힘 조절 능력)

### 1.2 제약사항
- 잔디 상태(그레인, 습도), 바람, 경사는 **측정 불가**
- 평평한 그린에서의 **기준 거리 캘리브레이션** 필수
- 손목 움직임 기반 측정이므로 개인별 스윙 특성 반영 필요

---

## 2. 필요 센서 및 데이터

### 2.1 Galaxy Watch 7/8 주요 센서

| 센서 | 용도 | 샘플링 레이트 권장 |
|------|------|-------------------|
| **가속도계 (Accelerometer)** | 스윙 가속도, 임팩트 감지 | 100-200 Hz |
| **자이로스코프 (Gyroscope)** | 손목 회전 각속도, 스윙 템포 | 100-200 Hz |
| **자력계 (Magnetometer)** | 스윙 방향성, 페이스 각도 추정 (옵션) | 50 Hz |
| **기압계 (Barometer)** | 고도 변화 감지 (경사 보정 참고용, 옵션) | 10 Hz |

### 2.2 핵심 센서 데이터

#### 2.2.1 가속도계 (최우선)
- **3축 가속도** (X, Y, Z): 손목의 직선 가속도
- **측정 목표**:
  - 백스윙 최대 속도 (테이크백 끝)
  - 다운스윙 최대 가속도 (임팩트 직전)
  - 임팩트 순간의 가속도 피크
  - 팔로우스루 감속 패턴

#### 2.2.2 자이로스코프 (보조)
- **3축 각속도** (Pitch, Roll, Yaw)
- **측정 목표**:
  - 스윙 템포 (백스윙 → 다운스윙 시간 비율)
  - 손목 코킹/언코킹 각속도
  - 스윙 평면 일관성

#### 2.2.3 파생 데이터
- **속도 (Velocity)**: 가속도 적분 (∫a dt)
- **변위 (Displacement)**: 속도 적분 (∫v dt) - 드리프트 보정 필요
- **운동 에너지**: E = ½mv² (손목+클럽 질량 고정)
- **임팩트 강도**: 임팩트 순간 가속도의 적분값 (Impulse)

---

## 3. 측정 알고리즘

### 3.1 퍼팅 스트로크 감지 (Stroke Detection)

#### Phase 1: 정지 상태 (Ready)
- 손목 가속도 < 0.3 m/s² (1초 이상 유지)
- 이 상태에서 사용자가 "측정 시작" 또는 자동 감지 모드

#### Phase 2: 백스윙 감지 (Backswing)
- 가속도 증가 감지 (임계값: 1.0 m/s²)
- 자이로 Y축 (손목 회전) 음수 방향 이동
- 최대 백스윙 위치: 각속도가 0에 근접 (방향 전환)

#### Phase 3: 다운스윙 & 임팩트 (Downswing & Impact)
- 가속도 급증 (피크 감지, 보통 5-20 m/s²)
- **임팩트 순간**: 가속도 최대값 직후 급격한 감소 (볼과 접촉)
- 측정 윈도우: 임팩트 전후 50-100ms

#### Phase 4: 팔로우스루 (Follow-through)
- 가속도 감소 → 정지 상태로 복귀
- 전체 스트로크 시간: 보통 0.5-1.5초

### 3.2 거리 예측 모델

#### 모델 A: 임팩트 속도 기반 (단순)
```
예상 거리 (m) = k × v_impact
- v_impact: 임팩트 순간 손목 속도 (m/s)
- k: 캘리브레이션 상수 (사용자별 고유값, 보통 1.5-3.0)
```

#### 모델 B: 운동 에너지 기반 (중급)
```
예상 거리 (m) = α × √(E_kinetic) + β
- E_kinetic = ½m × v²
- α, β: 캘리브레이션 파라미터
```

#### 모델 C: 임펄스 기반 (고급, 권장)
```
예상 거리 (m) = c₁ × I + c₂ × v_max + c₃ × T_swing + c₄
- I = ∫(F·dt) ≈ ∫(a·dt): 임팩트 구간 가속도 적분
- v_max: 최대 속도
- T_swing: 백스윙 → 임팩트 시간
- c₁, c₂, c₃, c₄: 캘리브레이션 계수 (다중 회귀)
```

#### 모델 선택 권장사항
1. **MVP**: 모델 A (구현 간단, 정확도 70-80%)
2. **정식 버전**: 모델 C (정확도 85-95%, 개인별 스윙 특성 반영)

### 3.3 데이터 전처리

#### 3.3.1 노이즈 필터링
- **Butterworth Low-Pass Filter** (차단 주파수 20-30 Hz)
  - 고주파 센서 노이즈 제거
  - 손떨림 제거
- **Kalman Filter** (옵션): 속도/변위 추정 시 드리프트 보정

#### 3.3.2 중력 보정
- 가속도계는 중력 가속도(9.8 m/s²) 포함
- 자이로 데이터 활용하여 기기 자세 추정 → 중력 벡터 제거
- **Gravity Compensation**:
  ```
  a_linear = a_measured - g_vector
  ```

#### 3.3.3 좌표계 정규화
- 워치 착용 방향(왼손/오른손), 기기 방향 무관하게 동작
- 센서 좌표 → 손목 고유 좌표계 변환 (calibration 시 결정)

---

## 4. 캘리브레이션 프로세스

### 4.1 초기 캘리브레이션 (필수)

#### 단계 1: 환경 설정
- 평평한 연습 그린 (경사 < 0.5도)
- 퍼팅 목표 거리 설정: 1m, 2m, 3m, 5m, 10m (5개 거리)

#### 단계 2: 거리별 측정 (각 거리당 5회)
1. 사용자가 목표 거리 입력 (예: 3m)
2. 5회 퍼팅 실행
3. 각 스트로크의 센서 데이터 + 실제 정지 거리 기록
4. 총 25개 데이터 포인트 수집

#### 단계 3: 모델 학습
- 입력: [임펄스, 최대속도, 스윙시간] 벡터
- 출력: 실제 거리
- **다중 선형 회귀** 또는 **다항 회귀**로 계수 도출
- 검증: R² > 0.85 목표

#### 단계 4: 모델 저장
- 사용자별 계수 (c₁, c₂, c₃, c₄) 로컬 저장
- 유효기간: 무제한 (재캘리브레이션 언제든지 가능)

### 4.2 빠른 재캘리브레이션 (옵션)

- 1개 거리(예: 3m)에서 5회 측정
- 기존 모델의 스케일 인자만 조정 (비례 보정)
- 사용 시나리오: 그린 속도가 다른 골프장 방문 시

### 4.3 캘리브레이션 품질 검증

- **일관성 체크**: 동일 거리 5회 측정의 표준편차 < 10%
- **선형성 체크**: 거리-센서값 상관계수 R > 0.9
- 품질 불합격 시 재측정 요청

---

## 5. UI/UX 설계 (WearOS)

### 5.1 화면 구성

#### 5.1.1 메인 대시보드 (Home Screen)
```
┌─────────────────────┐
│   12:34 PM          │  ← TimeText (상단)
│                     │
│   PuttMeter         │  ← 앱 타이틀 (중앙 상단)
│                     │
│  ┌───────────────┐  │
│  │   📏 측정     │  │  ← 큰 버튼 (메인 기능)
│  └───────────────┘  │
│                     │
│  ⚙️ 캘리브레이션   │  ← 서브 메뉴
│  📊 히스토리       │
│  ⚙️ 설정          │
└─────────────────────┘
```

#### 5.1.2 측정 화면 (Measurement Screen)
```
┌─────────────────────┐
│   측정 준비 중...   │  ← 상태 메시지
│                     │
│      🏌️            │  ← 애니메이션 아이콘
│                     │
│   손목을 자연스럽게  │
│   위치시키고        │
│   퍼팅하세요        │
│                     │
│  [🔴 측정 시작]     │  ← 큰 원형 버튼
└─────────────────────┘

[측정 중]
┌─────────────────────┐
│   측정 중...        │
│                     │
│    ••• 감지 중      │  ← 펄스 애니메이션
│                     │
│   스트로크를        │
│   실행하세요        │
│                     │
│  [⏹️ 취소]          │
└─────────────────────┘

[결과 표시]
┌─────────────────────┐
│   📏 예상 거리      │
│                     │
│      4.2 m          │  ← 대형 텍스트
│                     │
│  힘: ████░░░░ 60%   │  ← 시각적 인디케이터
│  템포: 0.8초        │
│                     │
│  [✅ 저장] [↻ 재측정]│
└─────────────────────┘
```

#### 5.1.3 캘리브레이션 화면
```
┌─────────────────────┐
│  캘리브레이션 (1/5) │  ← 진행 상황
│                     │
│   목표 거리: 1m     │  ← 큰 텍스트
│                     │
│   [0/5] 완료        │  ← 진행 카운터
│                     │
│  퍼팅 후 실제 정지   │
│  거리를 확인하고     │
│  다음 버튼을 누르세요│
│                     │
│  [🏌️ 측정 시작]     │
└─────────────────────┘

[측정 후]
┌─────────────────────┐
│  캘리브레이션 (1/5) │
│                     │
│   실제 거리 입력    │
│                     │
│   ┌─────────────┐   │
│   │   1.2  m    │   │  ← 입력 필드
│   └─────────────┘   │
│                     │
│  [⬅️ 0.1m 단위]      │  ← 조정 버튼
│  [➡️]               │
│                     │
│  [다음 (2/5)]       │
└─────────────────────┘
```

#### 5.1.4 히스토리 화면
```
┌─────────────────────┐
│   최근 퍼팅 기록    │
│                     │
│  ┌─────────────────┐│
│  │ 4.2m  60% 힘   ││  ← 스크롤 리스트
│  │ 14:23  오늘     ││
│  └─────────────────┘│
│  ┌─────────────────┐│
│  │ 3.8m  55% 힘   ││
│  │ 14:20  오늘     ││
│  └─────────────────┘│
│                     │
│  📈 통계 보기       │  ← 하단 버튼
└─────────────────────┘

[통계 상세]
┌─────────────────────┐
│   이번 주 통계      │
│                     │
│  평균 거리: 4.1m    │
│  총 퍼팅: 47회      │
│                     │
│  거리 분포:         │
│  3-5m: ████░ 80%   │
│  5-7m: ██░░░ 15%   │
│  7m+:  █░░░░  5%   │
│                     │
│  일관성: ⭐⭐⭐⭐   │
└─────────────────────┘
```

### 5.2 UX 고려사항

#### 5.2.1 손목 제스처
- **손목 올리기 → 자동 활성화**: 워치 페이스에서 바로 측정 화면
- **시계 버튼 길게 누르기**: 긴급 측정 모드 진입

#### 5.2.2 햅틱 피드백
- 스트로크 감지 시작: 짧은 진동 1회
- 임팩트 감지: 강한 진동 1회
- 측정 완료: 부드러운 진동 2회
- 오류 발생: 긴 진동 3회

#### 5.2.3 음성 피드백 (옵션)
- "4.2미터" - TTS로 거리 읽어주기
- 블루투스 이어폰 연결 시 활성화

#### 5.2.4 Always-On Display
- 측정 화면은 **항상 켜짐** (타임아웃 없음)
- 배터리 절약: OLED 특성 활용 (검은 배경, 최소 픽셀)

#### 5.2.5 접근성
- 큰 터치 타겟 (최소 48dp × 48dp)
- 고대비 색상 (흰색 텍스트 / 검은 배경)
- TalkBack 지원 (contentDescription 설정)

---

## 6. 데이터 구조

### 6.1 측정 데이터 모델

```kotlin
data class PuttStroke(
    val timestamp: Instant,
    val sensorData: StrokeSensorData,
    val predictedDistance: Float,  // meters
    val actualDistance: Float?,    // meters (캘리브레이션 시만)
    val metrics: StrokeMetrics
)

data class StrokeSensorData(
    val accelerationSamples: List<Vector3>,  // m/s²
    val gyroSamples: List<Vector3>,          // rad/s
    val samplingRate: Int,                   // Hz
    val duration: Long                       // milliseconds
)

data class StrokeMetrics(
    val peakAcceleration: Float,      // m/s²
    val impactVelocity: Float,        // m/s
    val impulse: Float,               // N·s (approximation)
    val swingTime: Long,              // ms
    val backswingTime: Long,          // ms
    val tempoRatio: Float             // backswing / downswing
)

data class CalibrationProfile(
    val userId: String,
    val createdAt: Instant,
    val coefficients: RegressionCoefficients,
    val calibrationQuality: Float,    // R²
    val referenceStrokes: List<PuttStroke>
)

data class RegressionCoefficients(
    val c1: Float,  // impulse weight
    val c2: Float,  // velocity weight
    val c3: Float,  // time weight
    val c4: Float   // intercept
)
```

### 6.2 저장소

- **로컬 DB**: Room (SQLite)
  - `putt_strokes` 테이블: 모든 측정 기록
  - `calibration_profiles` 테이블: 사용자별 캘리브레이션
- **공유 저장소**: DataStore (설정값)
- **최대 보관 기간**: 90일 (자동 삭제 또는 사용자 설정)

---

## 7. 기술 스택

### 7.1 센서 처리
- **Android Sensor API**: `SensorManager`, `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`
- **샘플링**: `SENSOR_DELAY_GAME` (50Hz) 또는 `SENSOR_DELAY_FASTEST` (200Hz)
- **신호 처리**: Kotlin DSP 라이브러리 또는 자체 구현 (Butterworth, Kalman)

### 7.2 데이터 처리
- **Coroutines + Flow**: 비동기 센서 스트림 처리
- **ViewModel + StateFlow**: UI 상태 관리
- **Room**: 로컬 데이터베이스
- **DataStore**: 캘리브레이션 계수 저장

### 7.3 UI
- **Jetpack Compose for Wear OS**: 모든 UI
- **Wear Material 3**: 표준 컴포넌트 (ScalingLazyColumn, Chip, Button)
- **Horologist**: 센서 수집 헬퍼, 권한 관리

### 7.4 수학/ML
- **Apache Commons Math** (옵션): 회귀 분석
- 또는 **자체 구현**: 최소제곱법 (OLS) 간단 구현

---

## 8. 핵심 기능 우선순위

### Phase 1 (MVP) - 2주
- [x] 가속도계 데이터 수집 (100 Hz)
- [x] 간단한 스트로크 감지 (임계값 기반)
- [x] 모델 A (임팩트 속도 기반) 거리 예측
- [x] 1개 거리 간이 캘리브레이션 (3m × 5회)
- [x] 측정 화면 + 결과 표시 UI

### Phase 2 (정식 버전) - 4주
- [ ] 자이로스코프 통합 (멀티센서 퓨전)
- [ ] 모델 C (임펄스 기반) 고도화
- [ ] 5개 거리 전체 캘리브레이션
- [ ] 히스토리 및 통계 화면
- [ ] 노이즈 필터링 (Butterworth)

### Phase 3 (고급 기능) - 4주
- [ ] 템포 일관성 분석 (피드백 제공)
- [ ] 스윙 평면 일관성 측정
- [ ] 데이터 내보내기 (CSV)
- [ ] 애플 워치 버전 개발 (선택)

---

## 9. 예상 정확도 및 제약

### 9.1 정확도 목표
- **절대 오차**: ±0.3m (3m 이하), ±0.5m (3-10m)
- **상대 오차**: ±10% (캘리브레이션된 그린 기준)
- **재현성**: 동일 힘 5회 스윙 시 표준편차 < 0.2m

### 9.2 영향 요인
- ✅ **측정 가능**: 스윙 속도, 가속도, 템포
- ❌ **측정 불가**: 그린 속도, 경사, 바람, 볼 컨디션
- ⚠️ **부분 측정**: 퍼터 헤드 각도 (손목 각도로 간접 추정)

### 9.3 사용자 피드백 필수
- 앱 실행 시 면책 메시지: "평평한 그린 기준 측정이며, 실제 라운드 시 환경 변수에 따라 오차 발생"
- 캘리브레이션 재안내: "그린 속도가 다른 골프장에서는 재캘리브레이션 권장"

---

## 10. 참고 자료

### 10.1 센서 문서
- [Android Sensor API](https://developer.android.com/guide/topics/sensors/sensors_overview)
- [Galaxy Watch Sensor Specifications](https://developer.samsung.com/galaxy-watch)

### 10.2 골프 물리학
- 퍼팅 속도 vs 거리: 경험적으로 선형 관계 (평평한 그린 기준)
- 임펄스-운동량 정리: I = Δ(mv)
- 마찰 계수: 그린 속도에 따라 0.1-0.3 (Stimpmeter 기준)

### 10.3 유사 앱 참고
- Blast Golf (스윙 분석, IMU 기반)
- Zepp Golf (손목 센서)
- Arccos Caddie (거리 추정)

---

## 부록: 알고리즘 의사코드

### A. 스트로크 감지

```kotlin
sealed class StrokePhase {
    object Idle : StrokePhase()
    data class Backswing(val startTime: Long) : StrokePhase()
    data class Impact(val impactTime: Long, val peakAccel: Float) : StrokePhase()
    data class Complete(val stroke: PuttStroke) : StrokePhase()
}

fun detectStroke(accelStream: Flow<Vector3>): Flow<StrokePhase> = flow {
    var phase: StrokePhase = StrokePhase.Idle
    val buffer = mutableListOf<Vector3>()
    
    accelStream.collect { accel ->
        val magnitude = accel.magnitude()
        
        when (phase) {
            is Idle -> {
                if (magnitude > BACKSWING_THRESHOLD) {
                    phase = Backswing(startTime = now())
                    emit(phase)
                }
            }
            is Backswing -> {
                buffer.add(accel)
                if (magnitude > IMPACT_THRESHOLD) {
                    phase = Impact(now(), magnitude)
                    emit(phase)
                }
            }
            is Impact -> {
                buffer.add(accel)
                if (magnitude < COMPLETE_THRESHOLD && elapsedSince(phase.impactTime) > 200) {
                    val stroke = computeStroke(buffer)
                    phase = Complete(stroke)
                    emit(phase)
                    buffer.clear()
                    phase = Idle
                }
            }
        }
    }
}
```

### B. 거리 예측 (모델 C)

```kotlin
fun predictDistance(metrics: StrokeMetrics, profile: CalibrationProfile): Float {
    val c = profile.coefficients
    return c.c1 * metrics.impulse +
           c.c2 * metrics.impactVelocity +
           c.c3 * metrics.swingTime / 1000f +
           c.c4
}

fun computeImpulse(accelSamples: List<Float>, samplingRate: Int): Float {
    val dt = 1f / samplingRate
    return accelSamples.sum() * dt  // 단순 적분
}
```

### C. 캘리브레이션 (최소제곱법)

```kotlin
fun calibrate(strokes: List<PuttStroke>): CalibrationProfile {
    // X = [impulse, velocity, time, 1] (N × 4 matrix)
    // y = [actual_distance] (N × 1 vector)
    // β = (X'X)⁻¹ X'y
    
    val X = strokes.map { s ->
        floatArrayOf(
            s.metrics.impulse,
            s.metrics.impactVelocity,
            s.metrics.swingTime / 1000f,
            1f
        )
    }.toTypedArray()
    
    val y = strokes.map { it.actualDistance!! }.toFloatArray()
    
    val coeffs = leastSquares(X, y)  // 외부 라이브러리 or 자체 구현
    val rSquared = computeRSquared(X, y, coeffs)
    
    return CalibrationProfile(
        coefficients = RegressionCoefficients(
            c1 = coeffs[0],
            c2 = coeffs[1],
            c3 = coeffs[2],
            c4 = coeffs[3]
        ),
        calibrationQuality = rSquared,
        referenceStrokes = strokes
    )
}
```

---

## 문서 버전
- **v1.0**: 2025-10-06 (초안)
- **작성자**: PuttMeter 개발팀

