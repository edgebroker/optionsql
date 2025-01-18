package org.optionsql.strategy.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SqlUtil {

    public static String findClosestExpiration(Connection conn, String ticker, double targetDte) throws SQLException {
        String closestExpiration = null;
        String query = "SELECT expiration_date FROM ticker_expirations " +
                       "WHERE ticker_symbol = ? " +
                       "ORDER BY ABS(days_to_expiration - ?) LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, ticker);
            stmt.setDouble(2, targetDte);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    closestExpiration = rs.getString("expiration_date");
                }
            }
        }
        return closestExpiration;
    }
}
