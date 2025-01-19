SELECT
    CASE
        WHEN sentiment = 'Strong Bullish' THEN true
        ELSE false
        END AS passed
FROM sentiment_ticker_expiration ste
WHERE ticker_symbol = ?
  AND expiration_date = ?;
