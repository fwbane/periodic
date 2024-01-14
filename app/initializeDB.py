from Task import Task
from constants import *
from utils import *
import datetime
from uuid import uuid4


dishes=Task(thingID=uuid4(), name="Dishes", period=ONE_DAY - 100)
pilates=Task(thingID=uuid4(),  name="Pilates", period=ONE_DAY * 4)
yoga=Task(thingID=uuid4(),  name="Yoga", period=ONE_DAY * 4)
running=Task(thingID=uuid4(),  name="Running", period=ONE_DAY * 4)
bodyweight=Task(thingID=uuid4(),  name="Bodyweight", period=ONE_DAY * 4)
backup=Task(thingID=uuid4(),  name="Backup", period=ONE_DAY * 90)
sheets=Task(thingID=uuid4(),  name="Sheets", period=ONE_DAY * 30)
adventure=Task(thingID=uuid4(),  name="Adventure", period=ONE_DAY * 7)
nails=Task(thingID=uuid4(),  name="Nails", period=ONE_DAY * 7)
laundry=Task(thingID=uuid4(), name="Laundry", period=ONE_DAY * 7)

mytasks = [dishes, pilates, yoga, running, bodyweight, backup, sheets, adventure, nails, laundry]
for task in mytasks:
    print(task.name, task.print_period(), format_time(task.due), task.history)


def save_db(things, path=DB_PATH): # TODO: UPDATE instead of INSERT
    db = path
    conn = sqlite3.connect(db)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS things (id string PRIMARY KEY, name, period, history, comment, category)''')
    for thing in things:
        print(thing.name)
        mycommand = "INSERT INTO things VALUES ('{}', '{}', {}, '{}', '{}', '{}')".format(thing.thingID, thing.name, thing.period, ", ".join([x.strftime(TIME_FORMAT) for x in thing.history]), thing.comment, thing.category)
        print(mycommand)
        c.execute(mycommand)
    conn.commit()
    conn.close()

for path in DEV_DB_PATH, TEST_DB_PATH:
    save_db(mytasks, path=path)