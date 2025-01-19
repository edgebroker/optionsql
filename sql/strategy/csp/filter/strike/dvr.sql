SELECT
    CASE
        WHEN oc.put_dvr BETWEEN (at.balanced_dvr - 0.05) AND (at.balanced_dvr + 0.05) THEN true
        ELSE false
        END AS passed
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
