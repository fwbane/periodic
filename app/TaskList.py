import datetime
import heapq
from constants import TIME_FORMAT, ONE_DAY, ONE_HOUR, ONE_MINUTE, ONE_MONTH, ONE_WEEK, TWO_DAYS, DB_PATH
from utils import format_period, update_all_to_DB, format_time
from Task import Task
import sqlite3

class TaskList():
    def __init__(self, taskList=[]):
        self.tasks = taskList
        self.dict = {task.thingID:task for task in taskList}

    def get_all(self):
        return self.tasks

    def get_priority_list(self):
        return sorted(self.tasks, key=lambda t:t.due)

    def get_task(self, query):
        if isinstance(query, str):
            print("STRING", query)
            for task in self.tasks:
                if task.name.lower().strip() == query.lower().strip():
                    return task
            print("task not found!")
            return None
        elif isinstance(query, int):
            thistask = self.dict.get(query)
            if thistask:
                return thistask
            else:
                print("task not found!")
                return None
    
    def show(self, ):
        heap = []
        for task in self.tasks:
            tilldue = task.due - datetime.datetime.now()
            sec = int(tilldue.total_seconds())
            heapq.heappush(heap, (sec, task.name))
        returnlist = []
        heap.sort()
        for x in heap:
            name = x[1]
            due = x[0]
            due = format_period(due)
            returnlist.append("{}\t\t{}".format(name, due))
        print("\n".join(returnlist))
    
    def add(self, name="Untitled-Task-{}".format(datetime.datetime.today().strftime(TIME_FORMAT)), period=86400, last=datetime.datetime.now()):
        newTask = Task(name, period, last)
        thingID = newTask.thingID
        self.tasks.append(newTask)
        self.dict[thingID] = newTask

    def check(self, query, time=datetime.datetime.now(), *otherTimes): 
        task = self.get_task(query)
        if task:
            if len(otherTimes) > 0:
                for timestamp in otherTimes:
                    if isinstance(timestamp, datetime.datetime):
                        task.check(timestamp)
                    elif isinstance(timestamp, tuple):
                        try:
                            y, m, d = timestamp
                            timestamp = datetime.datetime(y,m,d)
                            task.check(timestamp)
                        except:
                            print("Could not parse time of {}".format(timestamp))
            if isinstance(time, datetime.datetime):
                task.check(time)
            elif isinstance(time, tuple):
                try:
                    y, m, d = time
                    time = datetime.datetime(y,m,d)
                    task.check(time)
                except:
                    print("Could not parse time of {}".format(time))
        else:
            print("task {} not found.".format(task))

    def update_to_DB(self):
        update_all_to_DB(self.get_all(), DB_PATH)

def new_task_list_from_db(path=DB_PATH): #Does it make sense to have this here? We import sqlite3 here just for this
    conn = sqlite3.connect(path)
    c = conn.cursor()
    things = c.execute('SELECT * FROM things')
    objectified_things = []
    for thing in things:
        thingID, name, period, history, comment, category = thing
        print(name, history)
        history = sorted(set([datetime.datetime.strptime(x, TIME_FORMAT) for x in history.split(", ")]))
        mytask = Task(name=name, period=period, history=history, comment=comment, category=category, thingID=thingID)
        objectified_things.append(mytask)
    newlist = TaskList(objectified_things)
    conn.close()
    return newlist