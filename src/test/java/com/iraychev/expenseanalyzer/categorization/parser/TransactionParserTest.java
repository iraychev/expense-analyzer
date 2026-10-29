package com.iraychev.expenseanalyzer.categorization.parser;

import com.iraychev.expenseanalyzer.categorization.ParsedTransaction;
import com.iraychev.expenseanalyzer.categorization.RawTransaction;
import com.iraychev.expenseanalyzer.categorization.TransactionDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionParserTest {

    private static final TransactionParsers PARSERS =
            new TransactionParsers(new UniCreditParser(), new RevolutParser(), new DskParser());

    private static final String CARD = "ПОС 4.10 BGN авт.код:984725-SOF.RAZSAD.1130/RUSE/TID:93925306/PAN:5169****9015/CT:08/NOM::MDU,Операция с карта";

    @Test
    void uniCreditCardMerchantIsTextAfterAuthCodeUpToFirstSlash() {
        ParsedTransaction p = PARSERS.parse(new RawTransaction("UNICREDIT_UNCRBGSF", new BigDecimal("-4.10"),
                CARD, null, "AC1", List.of()));

        assertEquals("sof razsad 1130", p.merchantKey());
        assertEquals(TransactionDirection.EXPENSE, p.direction());
        assertEquals("AC1", p.bankTypeCode());
        assertEquals("Card payment at: SOF.RAZSAD.1130", p.aiText());
    }

    @Test
    void uniCreditMerchantKeepsCyrillicAndDigitsAndIsStableAcrossCaseAndPadding() {
        String a = "ПОС 5.00 BGN авт.код:111111-Кауфланд 123 /СОФИЯ/PAN:5169****9015/CT:08,Операция с карта";
        String b = "ПОС 7.00 BGN авт.код:222222-КАУФЛАНД   123/София/PAN:5169****9015/CT:08,Операция с карта";

        String keyA = PARSERS.parse(new RawTransaction(null, new BigDecimal("-5"), a, null, null, List.of())).merchantKey();
        String keyB = PARSERS.parse(new RawTransaction(null, new BigDecimal("-7"), b, null, null, List.of())).merchantKey();

        assertEquals("кауфланд 123", keyA);
        assertEquals(keyA, keyB);
    }

    @Test
    void uniCreditUrlMerchantIsNotCutAtTheSchemeSlashes() {
        String text = "Плащане /импринтер/ 23.89 BGN авт.код:774214-HTTP://WWW.DOMINOS.BG/SOFIYA/PAN:5169****9015/CT:08,Операция с карта";
        ParsedTransaction p = PARSERS.parse(new RawTransaction("UNICREDIT_UNCRBGSF", new BigDecimal("-23.89"), text, null, "AC1", List.of()));
        assertEquals("dominos bg", p.merchantKey());
    }

    @Test
    void uniCreditRevolutTopUpWithVaryingDigitsIsATransfer() {
        String text = "ПОС 100.00 BGN авт.код:907964-Revolut**8270*/Dublin/IRL/PAN:5169****9015/CT:08,Операция с карта";
        ParsedTransaction p = PARSERS.parse(new RawTransaction("UNICREDIT_UNCRBGSF", new BigDecimal("-100"), text, null, "AC1", List.of()));
        assertEquals("revolut", p.merchantKey());
        assertEquals(TransactionDirection.TRANSFER, p.direction());
    }

    @Test
    void uniCreditNonCardRowsUseCounterpartyAndTypeCode() {
        ParsedTransaction out = PARSERS.parse(new RawTransaction("UNICREDIT_UNCRBGSF", new BigDecimal("-20"),
                "РД - Наско и Георги,Издаден вътр.банков превод", "АНГЕЛ НИКОЛАЕВ БОТЕВ", "TF1", List.of()));
        assertEquals(TransactionDirection.TRANSFER, out.direction());
        assertEquals("ангел николаев ботев", out.merchantKey());

        ParsedTransaction fee = PARSERS.parse(new RawTransaction("UNICREDIT_UNCRBGSF", new BigDecimal("-1.20"),
                "BLINK Такса за междубанков превод", null, "C24", List.of()));
        assertEquals(TransactionDirection.EXPENSE, fee.direction());
        assertNull(fee.merchantKey());
    }

    @Test
    void revolutUsesCounterpartyNameAndProprietaryCode() {
        ParsedTransaction card = PARSERS.parse(new RawTransaction("REVOLUT_REVOGB21", new BigDecimal("-10.57"),
                "Lfs Eood", "Lfs Eood", "CARD_PAYMENT", List.of()));
        assertEquals("lfs eood", card.merchantKey());
        assertEquals(TransactionDirection.EXPENSE, card.direction());

        ParsedTransaction transfer = PARSERS.parse(new RawTransaction("REVOLUT_REVOGB21", new BigDecimal("-15"),
                "To JOHN SMITH Aloo", "JOHN SMITH", "TRANSFER", List.of()));
        assertEquals(TransactionDirection.TRANSFER, transfer.direction());
        assertEquals("john smith", transfer.merchantKey());
    }

    @Test
    void parserIsChosenByContentWhenInstitutionIsUnknown() {
        assertEquals("sof razsad 1130",
                PARSERS.parse(new RawTransaction(null, new BigDecimal("-1"), CARD, null, null, List.of())).merchantKey());
        assertEquals("lfs eood",
                PARSERS.parse(new RawTransaction(null, new BigDecimal("-1"), "x", "Lfs Eood", "CARD_PAYMENT", List.of())).merchantKey());
    }

    @Test
    void genericParserFallsBackToCounterpartyAndAmountSign() {
        ParsedTransaction income = PARSERS.parse(new RawTransaction("SOME_OTHER_BANK", new BigDecimal("50"),
                "Invoice 5", "ACME Ltd", null, List.of()));
        assertEquals(TransactionDirection.INCOME, income.direction());
        assertEquals("acme ltd", income.merchantKey());
        assertEquals("income:acme ltd", income.cacheKey());
    }

    @Test
    void aiTextHasNoMaskedPanIbanTerminalPhoneOrHolderName() {
        String text = "Превод от Ivaylo Raychev BG16UNCR70001598168484 PAN:5169****9015 TID:93925306 +359 88 840 8422 "
                + "5000029196;0242166281 ИВАЙЛО МАРТИНОВ РАЙЧЕВ, застраховка";
        ParsedTransaction p = PARSERS.parse(new RawTransaction("SOME_OTHER_BANK", new BigDecimal("-3"), text, null, null,
                List.of("Ivaylo Raychev", "ИВАЙЛО МАРТИНОВ РАЙЧЕВ")));

        String ai = p.aiText();
        assertFalse(ai.contains("BG16"), ai);
        assertFalse(ai.contains("5169"), ai);
        assertFalse(ai.contains("93925306"), ai);
        assertFalse(ai.contains("840"), ai);
        assertFalse(ai.contains("0242166281"), ai);
        assertFalse(ai.toLowerCase().contains("raychev"), ai);
        assertFalse(ai.toLowerCase().contains("райчев"), ai);
        assertTrue(ai.contains("застраховка"), ai);
    }

    @Test
    void merchantKeyHelperCollapsesPunctuation() {
        assertEquals("paypal steam games", TextUtil.merchantKey("PAYPAL *STEAM GAMES"));
        assertEquals("emag bg", TextUtil.merchantKey("eMAG.bg"));
        assertNull(TextUtil.merchantKey("   "));
        assertNull(TextUtil.merchantKey("***"));
    }
}
