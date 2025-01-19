SELECT
    CASE
        WHEN oc.put_oi >= at.median_oi THEN true
        ELSE false
        END AS passed,
    oc.put_oi as option,
    '>= '||at.median_oi as threshold
FROM
    optionchains oc
        JOIN
    adaptive_thresholds at
    ON oc.ticker_symbol = at.ticker_symbol AND oc.expiration_date = at.expiration_date
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
