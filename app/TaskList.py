import datetime
import heapq
from constants import TIME_FORMAT, ONE_DAY, ONE_HOUR, ONE_MINUTE, ONE_MONTH, ONE_WEEK, TWO_DAYS, DB_PATH
from utils import format_period, update_many_tasks_to_DB, get_all_tasks_DB, format_time, add_new_task_to_DB, remove_task_from_DB
from Task import Task


class TaskList():
    def __init__(self, taskList=[]):
        self.tasks = taskList
        self.dict = {task.thingID:task for task in taskList}

    def __iter__(self):
        yield from self.tasks

    def __getitem__(self, index):
        return self.tasks[index]
    
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
    
    def delete(self, task):
        self.tasks.remove(task)
        del(self.dict[task.thingID])
        remove_task_from_DB(task.thingID)
        

    # def add(self, name="Untitled-Task-{}".format(datetime.datetime.today().strftime(TIME_FORMAT)), period=86400, last=datetime.datetime.now()):
    #     newTask = Task(name, period, last)
    #     thingID = newTask.thingID
    #     self.tasks.append(newTask)
    #     self.dict[thingID] = newTask

    def add(self, new_task):
        thingID = new_task.thingID
        self.tasks.append(new_task)
        self.dict[thingID] = new_task
        add_new_task_to_DB(new_task)


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
        update_many_tasks_to_DB(self.get_all(), DB_PATH)

def new_task_list_from_db(path=DB_PATH): 
    thinglist = get_all_tasks_DB(path)
    objectified_things = []
    for thing in thinglist:
        thingID, name, period, history, comment, category = thing
        print(thing)
        new_task = Task(name=name, period=period, history=history, comment=comment, category=category, thingID=thingID)
        objectified_things.append(new_task)
    newlist = TaskList(objectified_things)
    return newlist