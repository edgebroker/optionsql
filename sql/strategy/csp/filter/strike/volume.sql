SELECT
    CASE
        WHEN oc.put_volume >= at.median_volume THEN true
        ELSE false
        END AS passed,
    oc.put_volume as option,
    '>=  '||at.median_volume as threshold
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
