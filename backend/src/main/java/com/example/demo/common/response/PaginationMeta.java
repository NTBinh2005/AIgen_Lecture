package com.example.demo.common.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaginationMeta {
    private Integer pageNum;
    private Integer pageSize;
    private Long totalItems;
    private Integer totalPages;
}
