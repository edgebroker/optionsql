-- Drop the table if it already exists
DROP TABLE IF EXISTS portfolio_stocks;

-- Create the portfolio_stocks table
CREATE TABLE portfolio_stocks (
                                  ticker_symbol VARCHAR(10) NOT NULL,
                                  assigned_at NUMERIC(10, 2) NOT NULL,  -- Represents the price at which the stock was assigned
                                  quantity INTEGER NOT NULL,
                                  PRIMARY KEY (ticker_symbol, assigned_at)
);
