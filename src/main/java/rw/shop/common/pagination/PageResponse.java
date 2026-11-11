package rw.shop.common.pagination;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {
}
