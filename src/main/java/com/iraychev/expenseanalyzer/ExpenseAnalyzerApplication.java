package com.iraychev.expenseanalyzer;

import com.iraychev.expenseanalyzer.config.properties.RsaKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RsaKeyProperties.class)
@EnableScheduling
public class ExpenseAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseAnalyzerApplication.class, args);
	}

}
