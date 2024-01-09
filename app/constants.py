import os

TIME_FORMAT="%Y-%m-%d-%H%M"
DEV_DB_PATH=os.path.join("db","dev.db")
PROD_DB_PATH=os.path.join("db","prod.db")
TEST_DB_PATH=os.path.join("db","test.db")
DB_PATH=DEV_DB_PATH # Change for production, testing
ONE_MONTH=2629728 # 86400 * 365.24 / 12  ??
ONE_WEEK=604800
TWO_DAYS=172800
ONE_DAY=86400
ONE_HOUR=3600
ONE_MINUTE=60
UNITSTR2SECS = {'Hours': ONE_HOUR, 'Days': ONE_DAY, 'Weeks': ONE_WEEK, 'Months': ONE_MONTH}
