SELECT
    CASE
        WHEN zge.zero_gamma_level IS NULL THEN TRUE  -- No ZGL → Pass
        WHEN oc.strike_price <= zge.zero_gamma_level THEN TRUE  -- Strike at/below ZGL → Pass
        ELSE FALSE  -- Strike above ZGL → Fail
        END AS passed,
    oc.strike_price as option,
    zge.zero_gamma_level as threshold
FROM optionchains oc
         LEFT JOIN zero_gamma_ticker_expiration zge
                   ON oc.ticker_symbol = zge.ticker_symbol
                       AND oc.expiration_date = zge.expiration_date
WHERE oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
