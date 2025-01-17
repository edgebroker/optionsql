SELECT
    CASE
        WHEN oc.put_vega_exp BETWEEN -1000 AND at.high_vega_exp THEN true
        ELSE false
        END AS passed,
    oc.put_vega_exp as option,
    '-1000 (static) to '||at.high_vega_exp as threshold
FROM
    optionchains oc
        JOIN
    adaptive_thresholds at
    ON oc.ticker_symbol = at.ticker_symbol
        AND oc.expiration_date = at.expiration_date
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
