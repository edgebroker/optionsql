SELECT
    CASE
        WHEN oc.put_ivr >= at.high_ivr THEN true
        ELSE false
        END AS passed,
     oc.put_ivr as option,
     '>= '||at.high_ivr as threshold
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
