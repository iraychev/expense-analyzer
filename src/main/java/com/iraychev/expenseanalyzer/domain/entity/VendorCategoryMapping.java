package com.iraychev.expenseanalyzer.domain.entity;

import javax.persistence.*;

@Entity
@Table(name = "vendor_category_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorCategoryMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String vendor;
    private String category;
}