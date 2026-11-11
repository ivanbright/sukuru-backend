package rw.shop.common.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class TenantDataSource extends DelegatingDataSource {

    private static final UUID NO_TENANT = new UUID(0L, 0L);

    public TenantDataSource(javax.sql.DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenant(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenant(connection);
        return connection;
    }

    private void applyTenant(Connection connection) throws SQLException {
        UUID businessId = TenantContext.businessId();
        String value = (businessId == null ? NO_TENANT : businessId).toString();
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.business_id', ?, false)")) {
            statement.setString(1, value);
            statement.execute();
        }
    }
}
