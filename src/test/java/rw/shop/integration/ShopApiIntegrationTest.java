package rw.shop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ShopApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("shop")
            .withUsername("app_migrator")
            .withPassword("migrator")
            .withInitScript("db/test-init.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = POSTGRES.getJdbcUrl() + "?stringtype=unspecified";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "app_runtime");
        registry.add("spring.datasource.password", () -> "runtime");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "app_migrator");
        registry.add("spring.flyway.password", () -> "migrator");
        registry.add("app.jwt.secret", () -> "integration-test-secret-value-32-bytes-min");
        registry.add("app.rate-limit.login-per-ip-per-minute", () -> 1000);
        registry.add("app.rate-limit.login-failures-per-email-per-15-minutes", () -> 1000);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Session(UUID businessId, String token) {
    }

    @Test
    void registersLogsInAndCreatesProduct() throws Exception {
        Session session = registerAndLogin("catalog");

        MvcResult created = mvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Sugar",
                                "unit", "kg",
                                "purchasePrice", "1000.00",
                                "sellingPrice", "1500.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sugar"))
                .andExpect(jsonPath("$.purchasePrice").value("1000.00"))
                .andReturn();

        JsonNode product = objectMapper.readTree(created.getResponse().getContentAsString());
        mvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Sugar"));
        mvc.perform(get("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value("1500.00"));
    }

    @Test
    void isolatesProductsBetweenBusinesses() throws Exception {
        Session first = registerAndLogin("tenant-a");
        Session second = registerAndLogin("tenant-b");

        MvcResult created = mvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(first.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Private item", "unit", "piece"))))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", bearer(second.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void receivesPurchaseSellsAndReplaysIdempotentSale() throws Exception {
        Session session = registerAndLogin("sales");
        String productId = createProduct(session, "Rice", "2000.00", "3000.00");

        MvcResult purchaseResult = mvc.perform(post("/api/v1/purchases")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of(
                                        "productId", productId,
                                        "quantity", "10.000",
                                        "unitCost", "2000.00"))))))
                .andExpect(status().isCreated())
                .andReturn();
        String purchaseId = objectMapper.readTree(purchaseResult.getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(post("/api/v1/purchases/" + purchaseId + "/receive")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        mvc.perform(get("/api/v1/inventory/stock")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value("10.000"));

        String saleKey = UUID.randomUUID().toString();
        String saleBody = json(Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", "3.000"))));
        MvcResult saleResult = mvc.perform(post("/api/v1/sales")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", saleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.totalAmount").value("9000.00"))
                .andExpect(jsonPath("$.referenceNumber").isNotEmpty())
                .andReturn();
        String saleId = objectMapper.readTree(saleResult.getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(get("/api/v1/sales?page=0&size=100")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(saleId))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("unpaid"));

        MvcResult replay = mvc.perform(post("/api/v1/sales")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", saleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(saleId);

        mvc.perform(get("/api/v1/inventory/stock")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value("7.000"));

        mvc.perform(post("/api/v1/sales")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("productId", productId, "quantity", "100.000"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", "9000.00", "paymentMethod", "cash"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/sales/" + saleId)
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("paid"));
    }

    private String createProduct(Session session, String name, String purchasePrice, String sellingPrice)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(session.token()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "unit", "kg",
                                "purchasePrice", purchasePrice,
                                "sellingPrice", sellingPrice))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String password = "password12345";
        MvcResult registration = mvc.perform(post("/api/v1/auth/register-business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "businessName", prefix + " shop",
                                "fullName", prefix + " owner",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID businessId = UUID.fromString(objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("businessId").asText());

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", password,
                                "businessId", businessId.toString()))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        return new Session(businessId, token);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
