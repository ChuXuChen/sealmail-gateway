package com.sealmail.app.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> items, long total, PageRequest pageRequest) {
        return PageResponse.<T>builder()
                .items(items)
                .total(total)
                .page(pageRequest.getPage())
                .size(pageRequest.getSize())
                .totalPages((int) Math.ceil((double) total / pageRequest.getSize()))
                .build();
    }
}
