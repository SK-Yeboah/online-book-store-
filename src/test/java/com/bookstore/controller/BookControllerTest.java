package com.bookstore.controller;

import com.bookstore.dto.request.BookRequest;
import com.bookstore.dto.request.UpdateStockRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BookController — integration")
class BookControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private BookRequest validBook;

    @BeforeEach
    void setUp() {
        validBook = new BookRequest(
                "Clean Code",
                "Robert C. Martin",
                "Programming",
                "978-0132350884",
                39.99,
                100,
                "A handbook of agile software craftsmanship"
        );
    }

    @Test
    @DisplayName("GET /api/books — 200 with empty page")
    void listBooks_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/books — 200 after admin creates book")
    @WithMockUser(roles = "ADMIN")
    void listBooks_afterCreate_returnsBook() throws Exception {
        createBook(validBook);

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"));
    }

    @Test
    @DisplayName("GET /api/books/{id} — 404 for unknown id")
    void getById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/books/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOK_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/books/{id} — 200 for existing book")
    @WithMockUser(roles = "ADMIN")
    void getById_existing_returns200() throws Exception {
        Long id = extractId(createBook(validBook));

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.isbn").value("978-0132350884"));
    }

    @Test
    @DisplayName("GET /api/books/isbn/{isbn} — 200 for existing ISBN")
    @WithMockUser(roles = "ADMIN")
    void getByIsbn_existing_returns200() throws Exception {
        createBook(validBook);

        mockMvc.perform(get("/api/books/isbn/{isbn}", "978-0132350884"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"));
    }

    @Test
    @DisplayName("POST /api/admin/books — 201 for admin")
    @WithMockUser(roles = "ADMIN")
    void create_admin_returns201() throws Exception {
        mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(validBook))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/books — 401 without auth")
    void create_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(validBook))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/admin/books — 403 for non-admin")
    @WithMockUser(roles = "USER")
    void create_user_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(validBook))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/books — 409 on duplicate ISBN")
    @WithMockUser(roles = "ADMIN")
    void create_duplicateIsbn_returns409() throws Exception {
        createBook(validBook);

        BookRequest dup = new BookRequest("Duplicate", "Author", "Cat",
                "978-0132350884", 10.0, 5, null);

        mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(dup))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_ISBN"));
    }

    @Test
    @DisplayName("PUT /api/admin/books/{id} — 200 on valid update")
    @WithMockUser(roles = "ADMIN")
    void update_valid_returns200() throws Exception {
        Long id = extractId(createBook(validBook));
        validBook.setTitle("Clean Code — Updated");

        mockMvc.perform(put("/api/admin/books/{id}", id)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(validBook))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code — Updated"));
    }

    @Test
    @DisplayName("PATCH /api/admin/books/{id}/stock — 200 updates stock")
    @WithMockUser(roles = "ADMIN")
    void updateStock_valid_returns200() throws Exception {
        Long id = extractId(createBook(validBook));

        mockMvc.perform(patch("/api/admin/books/{id}/stock", id)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(new UpdateStockRequest(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andExpect(jsonPath("$.inStock").value(false));
    }

    @Test
    @DisplayName("DELETE /api/admin/books/{id} — 204")
    @WithMockUser(roles = "ADMIN")
    void delete_existing_returns204() throws Exception {
        Long id = extractId(createBook(validBook));

        mockMvc.perform(delete("/api/admin/books/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isNotFound());
    }

    private String createBook(BookRequest req) throws Exception {
        return mockMvc.perform(post("/api/admin/books")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(req))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Long extractId(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asLong();
    }
}