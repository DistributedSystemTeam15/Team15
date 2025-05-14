package cm.lock;

/**
 * 닫힌 구간 [start, end] 과 소유자 정보를 나타내는 레코드.
 */
public record Interval(int start, int end, String owner) {

    /** owner 없이 시작-끝만 지정할 때 사용 */
    public Interval(int start, int end) {
        this(start, end, "");
    }

    /** 불변성 검증 */
    public Interval {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException(
                    "Interval: start(" + start + ") ≤ end(" + end + ") 가 보장돼야 합니다.");
        }
        owner = owner == null ? "" : owner;   // null → 빈 문자열로 통일
    }

    /** 두 반개방 구간 [start, end)이 겹치면 true */
    public boolean overlaps(Interval other) {
        return this.start < other.end && other.start < this.end;
    }

    /** 주어진 위치가 구간 안에 포함되면 true (start ≤ pos < end) */
    public boolean contains(int pos) {
        return start <= pos && pos < end;
    }

    /** owner 없이 복사본 생성 */
    public Interval stripOwner() {
        return new Interval(start, end);
    }
}
