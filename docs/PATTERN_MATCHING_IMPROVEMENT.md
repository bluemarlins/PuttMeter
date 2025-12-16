# 퍼팅 감지 로직 개선: 패턴 매칭 방식

> **목적**: 적분 오차를 제거하고 개인별 스윙 특성을 자동 반영하기 위한 패턴 학습 및 매칭 기반 거리 예측 시스템

---

## 1. 개요

### 1.1 기존 방식의 한계

현재 구현된 방식은 **가속도 → 속도 적분 → 거리 예측**의 단계를 거칩니다:

```
센서 데이터 → 속도 추정 (적분) → 캘리브레이션 함수 → 거리 예측
```

**문제점:**
- ⚠️ **적분 오차 누적**: 센서 노이즈와 드리프트로 인한 오차가 누적됨
- ⚠️ **개인차 반영 어려움**: 사람마다 다른 스윙 특성(템포, 힘, 스타일)을 캘리브레이션 함수만으로 반영하기 어려움
- ⚠️ **환경 변화 대응**: 그린 상태, 손목 위치 차이 등에 취약
- ⚠️ **복잡한 캘리브레이션**: 여러 거리 측정으로 이차/삼차 방정식 계수를 찾아야 함

### 1.2 새로운 접근: 패턴 매칭 방식

상용 골프 연습기에서 사용하는 **패턴 학습 및 매칭** 방식으로 전환:

```
스윙 패턴 저장 → 실제 스윙 패턴 추출 → 패턴 비교 → 가장 유사한 패턴의 거리 출력
```

**장점:**
- ✅ **적분 오차 제거**: 적분 없이 패턴만 비교하므로 오차 누적 없음
- ✅ **개인차 자동 반영**: 각 거리별 스윙 패턴을 저장하므로 개인 특성 자동 학습
- ✅ **단순한 학습 과정**: 거리별로 몇 번 스윙하면 자동으로 패턴 저장
- ✅ **정확도 향상**: 실제 사용자의 과거 스윙 데이터와 직접 비교

---

## 2. 핵심 개념

### 2.1 스윙 패턴이란?

**스윙 패턴**은 하나의 완전한 퍼팅 스윙 동안의 가속도 시계열 데이터입니다.

```
스윙 시작 → 백스윙 → 다운스윙 → 임팩트 → 팔로우스루 → 스윙 종료
   ↓
[가속도 샘플 1, 샘플 2, 샘플 3, ..., 샘플 N]
```

각 스윙은 **고유한 패턴**을 가집니다:
- 1m 퍼팅의 패턴 ≠ 3m 퍼팅의 패턴 ≠ 5m 퍼팅의 패턴
- 같은 거리라도 사람마다 다른 패턴
- 하지만 **같은 사람의 같은 거리 퍼팅은 유사한 패턴**

### 2.2 패턴 정규화

스윙 패턴을 비교하기 위해 **정규화(Normalization)**가 필수입니다.

**왜 정규화가 필요한가?**
- 스윙 시간이 다를 수 있음 (빠른 스윙 vs 느린 스윙)
- 스윙 강도가 다를 수 있음 (강한 스윙 vs 약한 스윙)
- 센서 샘플링 레이트 차이

**정규화 방법:**

1. **길이 정규화 (Time Normalization)**
   - 모든 패턴을 동일한 길이로 변환 (예: 50개 샘플)
   - 선형 보간(Linear Interpolation) 사용

2. **강도 정규화 (Amplitude Normalization)**
   - 최대값을 기준으로 0~1 범위로 정규화
   - 또는 L2 norm으로 정규화

```kotlin
// 예시: 패턴을 50개 샘플로 정규화
fun normalizePatternLength(pattern: FloatArray, targetLength: Int = 50): FloatArray {
    if (pattern.size == targetLength) return pattern
    
    val normalized = FloatArray(targetLength)
    val scale = (pattern.size - 1).toFloat() / (targetLength - 1)
    
    for (i in normalized.indices) {
        val sourceIndex = i * scale
        val index = sourceIndex.toInt()
        val fraction = sourceIndex - index
        
        if (index < pattern.size - 1) {
            normalized[i] = pattern[index] * (1 - fraction) + pattern[index + 1] * fraction
        } else {
            normalized[i] = pattern[index]
        }
    }
    return normalized
}

// 강도 정규화 (0~1 범위)
fun normalizeAmplitude(pattern: FloatArray): FloatArray {
    val max = pattern.maxOrNull() ?: 1f
    if (max == 0f) return pattern
    return FloatArray(pattern.size) { pattern[it] / max }
}
```

---

## 3. 시스템 아키텍처

### 3.1 전체 흐름

```
┌─────────────────────────────────────────────────────────────┐
│                    학습 모드 (Calibration)                   │
├─────────────────────────────────────────────────────────────┤
│  1. 사용자가 실제 거리 입력 (예: 3m)                        │
│  2. 스윙 실행 및 패턴 수집                                   │
│  3. 패턴 정규화                                              │
│  4. [거리: 3m, 패턴: [0.1, 0.3, ..., 0.8]] 형태로 저장     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  측정 모드 (Measurement)                     │
├─────────────────────────────────────────────────────────────┤
│  1. 스윙 실행 및 패턴 수집                                   │
│  2. 패턴 정규화                                              │
│  3. 저장된 모든 패턴과 유사도 계산                           │
│  4. 가장 유사한 패턴 찾기                                    │
│  5. 해당 패턴의 거리 출력                                    │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 데이터 구조

```kotlin
/**
 * 스윙 패턴 데이터
 */
data class SwingPattern(
    val id: Long = 0,
    val distance: Float,                    // 실제 거리 (m)
    val normalizedPattern: FloatArray,      // 정규화된 가속도 패턴 (50개 샘플)
    val timestamp: Long = System.currentTimeMillis(),
    val algorithm: String = "ACCEL_MAGNITUDE",  // 패턴 추출 알고리즘
    val metadata: PatternMetadata? = null   // 추가 메타데이터
)

/**
 * 패턴 메타데이터
 */
data class PatternMetadata(
    val swingDuration: Long,                // 스윙 시간 (ms)
    val maxAcceleration: Float,             // 최대 가속도 (m/s²)
    val averageAcceleration: Float,         // 평균 가속도 (m/s²)
    val peakIndex: Int                      // 최대 가속도 인덱스
)

/**
 * 패턴 매칭 결과
 */
data class PatternMatchResult(
    val matchedPattern: SwingPattern,       // 매칭된 패턴
    val similarity: Float,                  // 유사도 (0.0 ~ 1.0)
    val distance: Float                     // 예측 거리 (m)
)
```

---

## 4. 핵심 알고리즘

### 4.1 패턴 추출 (Pattern Extraction)

스윙 감지 후 가속도 시계열을 수집합니다.

```kotlin
class PatternExtractor {
    private val patternBuffer = mutableListOf<Float>()
    private var isCollecting = false
    
    /**
     * 스윙 시작 시 패턴 수집 시작
     */
    fun startCollection() {
        patternBuffer.clear()
        isCollecting = true
    }
    
    /**
     * 센서 데이터 추가
     */
    fun addSample(accelerationMagnitude: Float) {
        if (isCollecting) {
            patternBuffer.add(accelerationMagnitude)
        }
    }
    
    /**
     * 스윙 종료 시 패턴 추출 및 정규화
     */
    fun extractPattern(): FloatArray? {
        if (patternBuffer.isEmpty()) return null
        
        // 원본 패턴
        val rawPattern = patternBuffer.toFloatArray()
        
        // 1. 길이 정규화 (50개 샘플로)
        val lengthNormalized = normalizePatternLength(rawPattern, targetLength = 50)
        
        // 2. 강도 정규화 (0~1 범위)
        val amplitudeNormalized = normalizeAmplitude(lengthNormalized)
        
        isCollecting = false
        return amplitudeNormalized
    }
}
```

### 4.2 패턴 유사도 계산 (Pattern Similarity)

두 패턴 간의 유사도를 계산하는 방법:

**1. 유클리드 거리 (Euclidean Distance)**
```kotlin
fun calculateEuclideanDistance(pattern1: FloatArray, pattern2: FloatArray): Float {
    require(pattern1.size == pattern2.size) { "Patterns must have same length" }
    
    var sum = 0f
    for (i in pattern1.indices) {
        val diff = pattern1[i] - pattern2[i]
        sum += diff * diff
    }
    return sqrt(sum)
}

// 유사도로 변환 (거리가 가까울수록 유사도 높음)
fun distanceToSimilarity(distance: Float): Float {
    return 1f / (1f + distance)  // 0~1 범위
}
```

**2. DTW (Dynamic Time Warping)** - 시간 축 왜곡 허용
```kotlin
/**
 * DTW를 사용하면 시간 축의 작은 차이를 허용하여 더 정확한 매칭 가능
 * 하지만 계산 비용이 높으므로 실시간 처리에는 부적합할 수 있음
 */
fun calculateDTW(pattern1: FloatArray, pattern2: FloatArray): Float {
    // DTW 구현 (복잡하므로 생략)
    // 참고: Apache Commons Math 또는 직접 구현
}
```

**3. 코사인 유사도 (Cosine Similarity)**
```kotlin
fun calculateCosineSimilarity(pattern1: FloatArray, pattern2: FloatArray): Float {
    require(pattern1.size == pattern2.size) { "Patterns must have same length" }
    
    var dotProduct = 0f
    var norm1 = 0f
    var norm2 = 0f
    
    for (i in pattern1.indices) {
        dotProduct += pattern1[i] * pattern2[i]
        norm1 += pattern1[i] * pattern1[i]
        norm2 += pattern2[i] * pattern2[i]
    }
    
    val denominator = sqrt(norm1) * sqrt(norm2)
    return if (denominator == 0f) 0f else dotProduct / denominator
}
```

**권장 방식:** 초기 구현은 **유클리드 거리**를 사용하고, 필요시 DTW로 전환

### 4.3 패턴 매칭 (Pattern Matching)

저장된 패턴 중에서 가장 유사한 패턴을 찾습니다.

```kotlin
class PatternMatcher(private val patternDatabase: List<SwingPattern>) {
    
    /**
     * 입력 패턴과 가장 유사한 패턴 찾기
     */
    fun findBestMatch(inputPattern: FloatArray): PatternMatchResult? {
        if (patternDatabase.isEmpty()) return null
        
        var bestPattern: SwingPattern? = null
        var bestSimilarity = 0f
        var minDistance = Float.MAX_VALUE
        
        for (pattern in patternDatabase) {
            // 유클리드 거리 계산
            val distance = calculateEuclideanDistance(inputPattern, pattern.normalizedPattern)
            
            // 더 가까운 패턴 찾기
            if (distance < minDistance) {
                minDistance = distance
                bestPattern = pattern
                bestSimilarity = distanceToSimilarity(distance)
            }
        }
        
        return bestPattern?.let {
            PatternMatchResult(
                matchedPattern = it,
                similarity = bestSimilarity,
                distance = it.distance
            )
        }
    }
    
    /**
     * 유사도가 일정 수준 이상인 패턴들의 가중 평균 거리 계산
     * (더 정확한 예측을 위해)
     */
    fun findWeightedAverageDistance(
        inputPattern: FloatArray,
        similarityThreshold: Float = 0.5f
    ): Float? {
        val candidates = patternDatabase.mapNotNull { pattern ->
            val distance = calculateEuclideanDistance(inputPattern, pattern.normalizedPattern)
            val similarity = distanceToSimilarity(distance)
            
            if (similarity >= similarityThreshold) {
                Pair(pattern, similarity)
            } else {
                null
            }
        }
        
        if (candidates.isEmpty()) return null
        
        // 가중 평균 계산
        var weightedSum = 0f
        var totalWeight = 0f
        
        for ((pattern, similarity) in candidates) {
            val weight = similarity * similarity  // 유사도의 제곱을 가중치로 사용
            weightedSum += pattern.distance * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0f) weightedSum / totalWeight else null
    }
}
```

---

## 5. Android Wear 환경 구현

### 5.1 데이터 저장소 설계

**Room Database 사용 권장** (복잡한 쿼리 및 관계 지원)

```kotlin
@Entity(tableName = "swing_patterns")
data class SwingPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val distance: Float,
    
    @ColumnInfo(name = "pattern_data")  // FloatArray는 직접 저장 불가
    val patternData: String,  // JSON 또는 ByteArray로 직렬화
    
    val timestamp: Long,
    val algorithm: String,
    val swingDuration: Long,
    val maxAcceleration: Float,
    val averageAcceleration: Float,
    val peakIndex: Int
)

@Dao
interface SwingPatternDao {
    @Query("SELECT * FROM swing_patterns ORDER BY distance, timestamp DESC")
    fun getAllPatterns(): Flow<List<SwingPatternEntity>>
    
    @Query("SELECT * FROM swing_patterns WHERE distance BETWEEN :minDist AND :maxDist")
    fun getPatternsByDistanceRange(minDist: Float, maxDist: Float): Flow<List<SwingPatternEntity>>
    
    @Insert
    suspend fun insertPattern(pattern: SwingPatternEntity): Long
    
    @Delete
    suspend fun deletePattern(pattern: SwingPatternEntity)
    
    @Query("DELETE FROM swing_patterns WHERE id = :id")
    suspend fun deletePatternById(id: Long)
    
    @Query("SELECT COUNT(*) FROM swing_patterns")
    suspend fun getPatternCount(): Int
}

@Database(entities = [SwingPatternEntity::class], version = 1, exportSchema = false)
abstract class PatternDatabase : RoomDatabase() {
    abstract fun patternDao(): SwingPatternDao
}
```

**FloatArray 직렬화/역직렬화:**
```kotlin
class FloatArrayConverter {
    fun toJson(floatArray: FloatArray): String {
        return floatArray.joinToString(",")
    }
    
    fun fromJson(json: String): FloatArray {
        return json.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }
}
```

### 5.2 ViewModel 통합

**CalibrationViewModel 수정:**
```kotlin
class CalibrationViewModel(
    private val sensorDataSource: SensorDataSource,
    private val context: Context,
    private val patternRepository: PatternRepository  // 새로 추가
) : ViewModel() {
    
    private val patternExtractor = PatternExtractor()
    private val patternMatcher = PatternMatcher(emptyList())
    
    // 패턴 DB 구독
    init {
        viewModelScope.launch {
            patternRepository.getAllPatterns().collect { patterns ->
                val swingPatterns = patterns.map { it.toSwingPattern() }
                patternMatcher.updatePatterns(swingPatterns)
            }
        }
    }
    
    /**
     * 거리 입력 시 패턴 저장
     */
    fun inputActualDistance(distance: Float) {
        val pattern = patternExtractor.extractPattern()
        if (pattern != null) {
            viewModelScope.launch {
                patternRepository.savePattern(
                    distance = distance,
                    pattern = pattern,
                    metadata = extractMetadata(pattern)
                )
            }
        }
    }
}
```

**MeasurementViewModel 수정:**
```kotlin
class MeasurementViewModel(
    private val sensorDataSource: SensorDataSource,
    private val context: Context,
    private val patternRepository: PatternRepository  // 새로 추가
) : ViewModel() {
    
    private val patternExtractor = PatternExtractor()
    private val patternMatcher = PatternMatcher(emptyList())
    
    /**
     * 스윙 감지 시 패턴 매칭
     */
    private fun onStrokeDetected() {
        val pattern = patternExtractor.extractPattern()
        if (pattern != null) {
            val matchResult = patternMatcher.findBestMatch(pattern)
            
            matchResult?.let { result ->
                _uiState.value = _uiState.value.copy(
                    predictedDistance = result.distance,
                    // 유사도가 너무 낮으면 신뢰도 낮음 표시
                    showLowConfidence = result.similarity < 0.3f
                )
            } ?: run {
                // 매칭되는 패턴이 없음
                _uiState.value = _uiState.value.copy(
                    predictedDistance = 0f,
                    error = "매칭되는 패턴이 없습니다. 캘리브레이션이 필요합니다."
                )
            }
        }
    }
}
```

### 5.3 Repository 패턴

```kotlin
interface PatternRepository {
    suspend fun savePattern(
        distance: Float,
        pattern: FloatArray,
        metadata: PatternMetadata
    )
    
    fun getAllPatterns(): Flow<List<SwingPattern>>
    suspend fun deletePattern(id: Long)
    suspend fun getPatternCount(): Int
}

class PatternRepositoryImpl(
    private val patternDao: SwingPatternDao,
    private val converter: FloatArrayConverter
) : PatternRepository {
    
    override suspend fun savePattern(
        distance: Float,
        pattern: FloatArray,
        metadata: PatternMetadata
    ) {
        val entity = SwingPatternEntity(
            distance = distance,
            patternData = converter.toJson(pattern),
            timestamp = System.currentTimeMillis(),
            algorithm = "ACCEL_MAGNITUDE",
            swingDuration = metadata.swingDuration,
            maxAcceleration = metadata.maxAcceleration,
            averageAcceleration = metadata.averageAcceleration,
            peakIndex = metadata.peakIndex
        )
        patternDao.insertPattern(entity)
    }
    
    override fun getAllPatterns(): Flow<List<SwingPattern>> {
        return patternDao.getAllPatterns()
            .map { entities ->
                entities.map { it.toSwingPattern(converter) }
            }
    }
    
    override suspend fun deletePattern(id: Long) {
        patternDao.deletePatternById(id)
    }
    
    override suspend fun getPatternCount(): Int {
        return patternDao.getPatternCount()
    }
}

// Extension 함수
fun SwingPatternEntity.toSwingPattern(converter: FloatArrayConverter): SwingPattern {
    return SwingPattern(
        id = this.id,
        distance = this.distance,
        normalizedPattern = converter.fromJson(this.patternData),
        timestamp = this.timestamp,
        algorithm = this.algorithm,
        metadata = PatternMetadata(
            swingDuration = this.swingDuration,
            maxAcceleration = this.maxAcceleration,
            averageAcceleration = this.averageAcceleration,
            peakIndex = this.peakIndex
        )
    )
}
```

---

## 6. 성능 최적화

### 6.1 실시간 처리 고려사항

**문제점:**
- 패턴 매칭은 패턴 수가 많아질수록 느려짐
- Wear OS는 CPU/메모리 제약이 있음

**해결책:**

1. **패턴 수 제한**
   - 거리별 최대 N개 패턴만 유지 (예: 거리당 5개)
   - 오래된 패턴 자동 삭제 또는 LRU 방식

2. **인덱싱 및 필터링**
   ```kotlin
   // 거리 범위로 필터링하여 비교 대상 축소
   fun findBestMatch(inputPattern: FloatArray, estimatedRange: ClosedFloatingPointRange<Float>): PatternMatchResult? {
       val candidates = patternDatabase.filter { 
           it.distance in estimatedRange 
       }
       // candidates만 비교
   }
   ```

3. **비동기 처리**
   ```kotlin
   // 패턴 매칭은 백그라운드 스레드에서
   viewModelScope.launch(Dispatchers.Default) {
       val result = patternMatcher.findBestMatch(pattern)
       withContext(Dispatchers.Main) {
           updateUI(result)
       }
   }
   ```

4. **캐싱**
   - 자주 사용되는 패턴들을 메모리에 캐시
   - 패턴 DB 변경 시에만 갱신

### 6.2 메모리 관리

- FloatArray 크기 고정 (50개 샘플 × 4 bytes = 200 bytes per pattern)
- 패턴 수 제한으로 전체 메모리 사용량 제어
- Room의 Flow를 사용하여 필요한 시점에만 로드

---

## 7. 사용자 경험 개선

### 7.1 학습 모드 UI

```
[캘리브레이션 화면]

거리 입력: [___] m
스윙 횟수: 3/5

[퍼팅하세요 버튼]

저장된 패턴:
- 1m: 5개 패턴 ✓
- 2m: 3개 패턴 ✓
- 3m: 5개 패턴 ✓
- 5m: 2개 패턴 (추가 권장)
```

### 7.2 측정 모드 UI

```
[측정 화면]

예측 거리: 3.2 m
신뢰도: 85%

매칭된 패턴: 3.0m (유사도 0.85)

[저장된 패턴 현황]
- 1m ~ 5m: 총 15개 패턴
```

### 7.3 패턴 관리

- 패턴 삭제 기능
- 패턴 통계 (거리별 분포)
- 패턴 품질 평가 (이상치 제거)

---

## 8. 구현 단계

### Phase 1: 기본 구현
1. `PatternExtractor` 클래스 구현
2. 패턴 정규화 함수 구현
3. `PatternMatcher` 기본 구현 (유클리드 거리)
4. Room Database 및 Repository 구현
5. CalibrationViewModel에 패턴 저장 기능 추가

### Phase 2: 측정 통합
1. MeasurementViewModel에 패턴 매칭 기능 추가
2. UI에 매칭 결과 표시
3. 신뢰도 표시 기능 추가

### Phase 3: 최적화
1. 성능 최적화 (필터링, 인덱싱)
2. 패턴 품질 관리 (이상치 제거)
3. 가중 평균 방식 적용

### Phase 4: 고급 기능
1. DTW 알고리즘 적용 (선택적)
2. 패턴 클러스터링 (유사 패턴 그룹화)
3. 머신러닝 기반 개선 (TensorFlow Lite, 선택적)

---

## 9. 기존 시스템과의 통합

### 9.1 하이브리드 방식

기존 방식과 패턴 매칭 방식을 **병행 사용**할 수 있습니다:

```kotlin
enum class PredictionMode {
    CALIBRATION_FUNCTION,  // 기존 방식 (캘리브레이션 함수)
    PATTERN_MATCHING,      // 새로운 방식 (패턴 매칭)
    HYBRID                 // 두 방식의 가중 평균
}

// 하이브리드 예측
val calibrationDistance = calibrationFunction.predict(speed)
val patternDistance = patternMatcher.findBestMatch(pattern)?.distance

val finalDistance = when (mode) {
    PredictionMode.CALIBRATION_FUNCTION -> calibrationDistance
    PredictionMode.PATTERN_MATCHING -> patternDistance ?: 0f
    PredictionMode.HYBRID -> {
        val w1 = if (patternCount > 5) 0.7f else 0.3f  // 패턴이 많으면 패턴 방식 비중 증가
        val w2 = 1f - w1
        calibrationDistance * w2 + (patternDistance ?: calibrationDistance) * w1
    }
}
```

### 9.2 마이그레이션 전략

1. **초기 단계**: 두 방식 모두 지원, 사용자가 선택
2. **패턴 축적**: 사용자가 패턴을 저장할수록 패턴 방식 정확도 향상
3. **전환 권장**: 패턴이 충분히 쌓이면 패턴 방식으로 전환 권장
4. **완전 전환**: 패턴 방식이 충분히 검증되면 기본값 변경

---

## 10. 향후 개선 방향

### 10.1 고급 패턴 분석

- **패턴 특징 추출**: 피크 위치, 기울기, 곡선 형태 등
- **패턴 클러스터링**: K-means 등을 사용한 자동 그룹화
- **이상치 탐지**: 비정상적인 스윙 자동 제외

### 10.2 머신러닝 통합

- **TensorFlow Lite 모델**: 패턴을 입력으로 받아 거리 예측
- **온디바이스 학습**: 사용자별 모델 자동 조정
- **전이 학습**: 다른 사용자의 패턴으로 초기 모델 학습

### 10.3 센서 융합

- **가속도 + 자이로**: 패턴에 자이로 데이터도 포함
- **다중 축 분석**: X, Y, Z 축 각각의 패턴 추출 및 융합
- **시간-주파수 분석**: FFT를 사용한 주파수 도메인 특징 추출

### 10.4 클라우드 동기화

- 패턴 데이터 클라우드 백업
- 여러 기기 간 패턴 공유
- 커뮤니티 패턴 데이터 활용 (익명화된 집계 데이터)

---

## 11. 참고 자료

- **DTW 알고리즘**: Dynamic Time Warping for sequence matching
- **Room Database**: Android 공식 문서
- **Kotlin Coroutines Flow**: 비동기 데이터 스트림 처리
- **상용 골프 연습기**: TrackMan, Foresight Sports 등의 접근 방식 참고

---

## 12. 결론

패턴 매칭 방식은 기존 적분 기반 방식의 한계를 극복하고, 개인별 스윙 특성을 자동으로 학습할 수 있는 강력한 접근법입니다. 

**핵심 포인트:**
1. ✅ 적분 오차 제거
2. ✅ 개인차 자동 반영
3. ✅ 단순한 학습 과정
4. ✅ 높은 정확도 잠재력

Android Wear 환경에서도 충분히 구현 가능하며, 단계적 접근으로 점진적으로 개선할 수 있습니다.

