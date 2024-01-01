import datetime
import heapq
import sqlite3
from .utils import format_time, format_period, add_new_task_to_DB



TIME_FORMAT="%Y-%m-%d-%H%M"
ONE_DAY=86400
ONE_HOUR=3600
ONE_MINUTE=60
DB_PATH='things.db'


class Task():
    def __init__(self, name="Untitled-Task-{}".format(datetime.datetime.today().strftime(TIME_FORMAT)), 
                period=86400, 
                history=[datetime.datetime.now()],
                comment="",
                category="", thingID=None):
        self.name = name
        self.period = period
        self.history=history
        self.due =  history[-1] + datetime.timedelta(seconds=period)
        self.comment = comment
        self.category = category
        global counter
        add = False
        if thingID is None:
            add = True
            thingID = counter
            counter += 1
        elif thingID >= counter:
            counter = thingID + 1
        self.thingID = thingID
        print(self.thingID, self.name, self.period, self.due, self.comment, self.category)
        if add:
            add_new_task_to_DB(self, path=DB_PATH)
    
    def check(self, time=datetime.datetime.now()):
        self.history.append(time)
        newdue = time + datetime.timedelta(seconds=self.period)
        if newdue > self.due:
            self.due = newdue
        
    
    def get_period(self,):
        return self.period
    
    def print_period(self, ):
        return format_period(self.get_period())

    def set_period(self, new_period):
        self.period = new_period
    
    def get_due(self):
        return self.due
    
    def print_due(self):
        return format_time(self.get_due())

    def set_due(self, new_due):
        self.due = new_due      

    def get_last(self):
        return self.history[-1]

    def print_last(self):
        return format_time(self.get_last())

    def get_name(self, ):
        return self.name

    def set_name(self, new_name):
        self.name = new_name      

    def get_comment(self, ):
        return self.comment

    def set_comment(self, new_comment):
        self.comment = new_comment     

    def get_category(self, ):
        return self.category

    def set_category(self, new_category):
        self.category = new_category    

    def get_thingID(self, ):
        return self.thingID

    def set_thingID(self, new_thingID):
        self.thingID = new_thingID