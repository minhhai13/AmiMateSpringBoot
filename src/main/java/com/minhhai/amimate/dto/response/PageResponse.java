package com.minhhai.amimate.dto.response;

import java.util.List;

public record PageResponse<T>(

        List<T> items,

        int page,

        int size,

        long totalElements,

        boolean hasNext) {

}
