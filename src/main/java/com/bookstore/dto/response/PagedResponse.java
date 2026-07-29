package com.bookstore.dto.response;

import java.util.List;



import lombok.Getter;
import org.springframework.data.domain.Page;



@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final Integer page;
    private final Integer size;
    private final Long totalElements;
    private final Integer totalPages;
    private final boolean last;

    private PagedResponse(Page<T> pageData){
        this.content = pageData.getContent();
        this.page = pageData.getNumber();
        this.size = pageData.getSize();
        this.totalElements = pageData.getTotalElements();
        this.totalPages = pageData.getTotalPages();
        this.last = pageData.isLast();
    }

    public static <T> PagedResponse<T> of(Page<T> page){
        return new PagedResponse<>(page);
    }
    
}
