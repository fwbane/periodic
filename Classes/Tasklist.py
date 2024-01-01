import datetime
import heapq
import sqlite3
from .Task import Task
from .utils import new_task_list_from_db, update_all_to_DB, format_time, format_period, add_new_task_to_DB


TIME_FORMAT="%Y-%m-%d-%H%M"
ONE_DAY=86400
ONE_HOUR=3600
ONE_MINUTE=60
DB_PATH='things.db'

class TaskList():
    def __init__(self, taskList=[]):
        self.tasks = taskList
        self.dict = {task.thingID:task for task in taskList}

    def get_all(self):
        return self.tasks

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
            #print(task.name, task.due, task.print_period())
            tilldue = task.due - datetime.datetime.now()
            sec = int(tilldue.total_seconds())
            #print(tilldue)
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

    def check(self, query, time=datetime.datetime.now()): 
        task = self.get_task(query)
        if task:
            task.check(time)
        else:
            print("task {} not found.".format(task))

    def update_to_DB(self):
        update_all_to_DB(self.get_all(), DB_PATH)