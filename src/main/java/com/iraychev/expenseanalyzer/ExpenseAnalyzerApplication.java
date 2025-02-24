package com.iraychev.expenseanalyzer;

import com.iraychev.expenseanalyzer.config.properties.RsaKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RsaKeyProperties.class)
public class ExpenseAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseAnalyzerApplication.class, args);
	}

}
