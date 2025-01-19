SELECT expiration_date
FROM ticker_expirations
WHERE ticker_symbol = ?
ORDER BY ABS(days_to_expiration - 45)
LIMIT 3 -- max 3 expiration dates