SELECT
    CASE
        WHEN coalesce(oc.put_ovr, 0) >= at.median_ovr THEN true
        ELSE false
        END AS passed,
    coalesce(oc.put_ovr, 0) as option,
    '>= '||at.median_ovr as threshold
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
