package com.iraychev.expenseanalyzer.controller;

import com.iraychev.expenseanalyzer.dto.CategoryDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CategoryController()).build();

    @Test
    void listsKeyLabelAndKindForEveryCategory() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("Supermarkets"))
                .andExpect(jsonPath("$[0].label").value("Supermarkets"))
                .andExpect(jsonPath("$[0].kind").value("EXPENSE"))
                .andExpect(jsonPath("$[?(@.key=='Salary')].kind").value("INCOME"))
                .andExpect(jsonPath("$[?(@.key=='Transfer in')].kind").value("TRANSFER"))
                .andExpect(jsonPath("$[?(@.key=='Fees')].kind").value("EXPENSE"));
    }

    @Test
    void containsTheOriginalCategoriesPlusTheNewOnes() {
        List<String> keys = new CategoryController().listCategories().stream().map(CategoryDto::key).toList();
        assertTrue(keys.containsAll(List.of("Supermarkets", "Financial Services", "Shopping", "Restaurants and bars",
                "Entertainment and Sport", "Traveling and Vacation", "Health and Beauty", "Other",
                "Salary", "Other income", "Transfer in", "Transfer out", "Fees")));
        assertEquals(13, keys.size());
    }
}
