package com.ticketmaster.common.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wrapper cho paginated responses. Dùng ở tất cả list/search endpoints.
 *
 * <p>Sử dụng static factory {@link #from(Page)} để convert từ Spring Data {@link Page}:
 * <pre>
 * Page&lt;EventResponse&gt; page = eventService.searchEvents(query, pageable);
 * return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
 * </pre>
 *
 * @param <T> kiểu dữ liệu của mỗi phần tử trong trang
 */
@Getter
@Builder
public class PageResponse<T> {

    /** Danh sách phần tử trong trang hiện tại. */
    private final List<T> content;

    /** Số trang hiện tại (bắt đầu từ 0). */
    private final int page;

    /** Số phần tử mỗi trang. */
    private final int size;

    /** Tổng số phần tử trên tất cả các trang. */
    private final long totalElements;

    /** Tổng số trang. */
    private final int totalPages;

    /** {@code true} nếu đây là trang đầu tiên. */
    private final boolean first;

    /** {@code true} nếu đây là trang cuối cùng. */
    private final boolean last;

    /** {@code true} nếu không có trang trước. */
    private final boolean empty;

    /**
     * Chuyển đổi Spring Data {@link Page} sang {@link PageResponse}.
     *
     * @param page kết quả page từ repository/service
     * @param <T>  kiểu phần tử
     * @return PageResponse tương ứng
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}