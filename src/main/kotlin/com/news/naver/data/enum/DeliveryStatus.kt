/**
 * 뉴스 전송 상태를 정의하는 Enum 클래스입니다.
 */
enum class DeliveryStatus {
    /** 전송 성공 */
    SUCCESS,
    /** 재시도 중 */
    RETRY,
    /** 전송 실패 */
    FAILED
}
