import datetime
import heapq
import itertools
import sqlite3
import sys
from Classes import utils, Task, Tasklist


TIME_FORMAT="%Y-%m-%d-%H%M"
ONE_DAY=86400
ONE_HOUR=3600
ONE_MINUTE=60
DB_PATH='things.db'

counter = 0


def main(argv):
    newlist = utils.new_task_list_from_db()
    newlist.show()
    for arg in argv:
        newlist.check(arg)
    #utils.update_all_to_DB(newlist.get_all(), DB_PATH)
    newlist.show()



if __name__ == "__main__":
  main(sys.argv[1:])