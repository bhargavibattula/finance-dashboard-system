package com.zorvyn.finance.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wraps Spring Data's Page into a clean JSON structure.
 * Hides Spring's internal Page implementation from API consumers.
 */
public class PagedResponse<T> {

    private List<T> content;
    private int     page;
    private int     size;
    private long    totalElements;
    private int     totalPages;
    private boolean first;
    private boolean last;

    private PagedResponse() {}

    /**
     * @param page          the raw Spring Page (for metadata)
     * @param mappedContent already-mapped DTOs — never raw entities
     */
    public static <T> PagedResponse<T> of(Page<?> page, List<T> mappedContent) {
        PagedResponse<T> r = new PagedResponse<>();
        r.content       = mappedContent;
        r.page          = page.getNumber();
        r.size          = page.getSize();
        r.totalElements = page.getTotalElements();
        r.totalPages    = page.getTotalPages();
        r.first         = page.isFirst();
        r.last          = page.isLast();
        return r;
    }

    public List<T> getContent()    { return content; }
    public int     getPage()       { return page; }
    public int     getSize()       { return size; }
    public long    getTotalElements(){ return totalElements; }
    public int     getTotalPages() { return totalPages; }
    public boolean isFirst()       { return first; }
    public boolean isLast()        { return last; }
}