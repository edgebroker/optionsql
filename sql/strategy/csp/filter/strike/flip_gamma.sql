SELECT
    CASE
        WHEN zge.flip_gamma IS NULL THEN TRUE  -- No flip gamma → Pass
        WHEN oc.strike_price < zge.zero_gamma_level THEN TRUE  -- Strike below zero gamma → Pass
        WHEN zge.flip_gamma <= (0.02 * oc.strike_price) THEN TRUE  -- Flip gamma is small
        ELSE FALSE
        END AS passed
FROM optionchains oc
         LEFT JOIN zero_gamma_ticker_expiration zge
                   ON oc.ticker_symbol = zge.ticker_symbol
                       AND oc.expiration_date = zge.expiration_date
WHERE oc.ticker_symbol = ? AND oc.expiration_date = ? AND oc.strike_price = ?;
