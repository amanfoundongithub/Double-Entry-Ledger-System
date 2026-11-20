package com.ledger_core.ledger_core.accounts.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "accounts")
@CompoundIndexes({
        @CompoundIndex(name = "idx_account_type_active", def = "{'type': 1, 'active': 1}")
})
public class Account {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String name;
    private AccountType type;
    private NormalSide normalSide;
    private CashFlowCategory cashFlowCategory;
    private boolean cashEquivalent;
    private boolean contra;
    private String currency;
    private boolean active;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    protected Account() {
    }

    public Account(
            String id,
            String code,
            String name,
            AccountType type,
            NormalSide normalSide,
            CashFlowCategory cashFlowCategory,
            boolean cashEquivalent,
            boolean contra,
            String currency,
            boolean active,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.code = requireNonBlank(code, "code");
        this.name = requireNonBlank(name, "name");
        this.type = Objects.requireNonNull(type, "type is required");
        this.normalSide = normalSide != null ? normalSide : type.defaultNormalSide();
        this.cashFlowCategory = cashFlowCategory != null ? cashFlowCategory : CashFlowCategory.NONE;
        this.cashEquivalent = cashEquivalent;
        this.contra = contra;
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency.trim().toUpperCase();
        this.active = active;
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Account create(
            String code,
            String name,
            AccountType type,
            NormalSide normalSide,
            CashFlowCategory cashFlowCategory,
            boolean cashEquivalent,
            boolean contra,
            String currency,
            String description,
            Instant now
    ) {
        NormalSide resolvedSide = normalSide != null
                ? normalSide
                : (contra ? type.defaultNormalSide().opposite() : type.defaultNormalSide());

        return new Account(
                null,
                code,
                name,
                type,
                resolvedSide,
                cashFlowCategory,
                cashEquivalent,
                contra,
                currency,
                true,
                description,
                now,
                now
        );
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt is required");
    }

    public void updateDetails(String name, String description, CashFlowCategory cashFlowCategory, boolean cashEquivalent, Instant now) {
        this.name = requireNonBlank(name, "name");
        this.description = description;
        this.cashFlowCategory = cashFlowCategory != null ? cashFlowCategory : CashFlowCategory.NONE;
        this.cashEquivalent = cashEquivalent;
        this.updatedAt = Objects.requireNonNull(now, "updatedAt is required");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public NormalSide getNormalSide() {
        return normalSide;
    }

    public CashFlowCategory getCashFlowCategory() {
        return cashFlowCategory;
    }

    public boolean isCashEquivalent() {
        return cashEquivalent;
    }

    public boolean isContra() {
        return contra;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}