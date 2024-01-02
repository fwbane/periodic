import sqlite3
import datetime
from constants import TIME_FORMAT, ONE_DAY, ONE_HOUR, ONE_MINUTE, ONE_WEEK, ONE_MONTH, DB_PATH

def format_time(time):
    if isinstance(time, datetime.datetime):
        return time.strftime(TIME_FORMAT)
    else:
        print("Please pass a datetime object! I can't work under these conditions!!")


def format_period(period):
    def format_plural(n, unit):
        if n == 1:
            return "{} {}".format(n, unit)
        else:
            return "{} {}s".format(n, unit)
    return_str = ""
    if period < 0:
        return "OVERDUE"
    elif period > ONE_MONTH:
        months = int(period/ONE_MONTH)
        days = int((period%ONE_MONTH)/ONE_DAY)
        return_str += format_plural(months, "month")
        if days > 0:
            return_str += ", {}".format(format_plural(days, "day"))
        return return_str
    elif period > ONE_DAY:
        days = int(period/ONE_DAY)
        hours = int((period%ONE_DAY)/ONE_HOUR)
        return_str += format_plural(days, "day")
        if hours > 0:
            return_str += ", {}".format(format_plural(hours, "hour"))
        return return_str
    else:
        hours = int(period/ONE_HOUR)
        minutes = int((period%ONE_HOUR)/ONE_MINUTE)
        return_str += format_plural(hours, "hour")
        if minutes > 0:
            return_str += ", {}".format(format_plural(minutes, "minute"))
        return return_str

def update_task_to_DB(thing, path=DB_PATH): 
    conn = sqlite3.connect(path)
    c = conn.cursor()
    mycommand = "UPDATE things set name = '{}', period = {}, history = '{}', comment = '{}', category = '{}' WHERE id = '{}';".format(thing.name, thing.period, ", ".join([x.strftime(TIME_FORMAT) for x in thing.history]), thing.comment, thing.category, thing.thingID)
    print(mycommand)
    c.execute(mycommand)
    conn.commit()
    conn.close()

def update_many_tasks_to_DB(things, path=DB_PATH): 
    db = path
    conn = sqlite3.connect(db)
    c = conn.cursor()
    mycommand = 'SELECT id FROM things;'
    thingIDs = [row[0] for row in c.execute(mycommand)]
    for thing in things:
        if thing.thingID in thingIDs:
            print(thing.name)
            mycommand = "UPDATE things set name = '{}', period = {}, history = '{}', comment = '{}', category = '{}' WHERE id = '{}';".format(thing.name, thing.period, ", ".join([x.strftime(TIME_FORMAT) for x in thing.history]), thing.comment, thing.category, thing.thingID)
            print(mycommand)
            c.execute(mycommand)
        else:
            print("ThingID {} not in DB! Adding...".format(thing.thingID))
            mycommand = "INSERT INTO things VALUES ({}, '{}', {}, '{}', '{}', '{}');".format(thing.thingID, thing.name, thing.period, ", ".join([x.strftime(TIME_FORMAT) for x in thing.history]), thing.comment, thing.category)
            print(mycommand)
            c.execute(mycommand)
        conn.commit()
    conn.close()

def add_new_task_to_DB(thing, path=DB_PATH): 
    db = path
    conn = sqlite3.connect(db)
    c = conn.cursor()
    mycommand = "INSERT INTO things VALUES ('{}', '{}', {}, '{}', '{}', '{}');".format(thing.thingID, thing.name, thing.period, ", ".join([x.strftime(TIME_FORMAT) for x in thing.history]), thing.comment, thing.category)
    c.execute(mycommand)
    conn.commit()
    conn.close()

def remove_task_from_DB(thingID, path=DB_PATH):
    db = path
    conn = sqlite3.connect(db)
    c = conn.cursor()
    mycommand = "DELETE FROM things WHERE id = '{}';".format(thingID)
    c.execute(mycommand)
    conn.commit()
    conn.close()

def read_db(path=DB_PATH):
    db = path
    conn = sqlite3.connect(db)
    c = conn.cursor()
    things = c.execute('SELECT * FROM things')
    conn.close()
    return things

def get_all_tasks_DB(path=DB_PATH):
    conn = sqlite3.connect(path)
    c = conn.cursor()
    things = c.execute('SELECT * FROM things')
    thinglist = []
    for thing in things:
        thingID, name, period, history, comment, category = thing
        history = sorted(set([datetime.datetime.strptime(x, TIME_FORMAT) for x in history.split(", ")]))
        thinglist.append([thingID,name,period,history,comment,category])
    conn.close()
    return thinglist
