WITH selected_option AS (
    SELECT oc.strike_price, t.current_price, oc.ticker_symbol
    FROM optionchains oc
             JOIN ticker t ON oc.ticker_symbol = t.ticker_symbol
    WHERE oc.ticker_symbol = ?
      AND oc.expiration_date = ?
      AND oc.strike_price = ?
)
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN TRUE  -- Key level exists between price and strike
        ELSE FALSE                   -- No support between price and strike
        END AS passed
FROM key_levels kl
         JOIN selected_option so
              ON kl.ticker_symbol = so.ticker_symbol
WHERE kl.strike_price BETWEEN so.strike_price AND so.current_price;


