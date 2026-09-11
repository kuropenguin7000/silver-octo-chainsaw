package io.kessai.wallet.shared.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI kessaiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("kessai wallet-service")
                .version("v1")
                .description("""
                        Wallets, accounts and the double-entry ledger.

                        Money is always an integer count of a currency's minor units \
                        (JPY has scale 0, so 1 unit = 1 yen). Never a decimal.

                        Errors are RFC 9457 problem+json. Branch on the `code` field, \
                        never on `detail`."""));
    }
}
