SELECT
    CASE
        WHEN (oc.put_vega_exp / NULLIF(ABS(oc.put_theta_decay_exp), 0)) <= 10 THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
