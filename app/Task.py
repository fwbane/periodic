import datetime
from constants import TIME_FORMAT, ONE_DAY, ONE_HOUR, ONE_MINUTE, ONE_MONTH, ONE_WEEK, TWO_DAYS, DB_PATH
from utils import format_period, format_time, add_new_task_to_DB
from uuid import uuid4

class Task():
    def __init__(self, thingID=uuid4(), name="Untitled-Task-{}".format(datetime.datetime.today().strftime(TIME_FORMAT)), 
                period=ONE_WEEK, 
                history=[datetime.datetime.now()],
                comment="",
                category=""):
        self.name = name
        self.period = period
        self.history=history
        self.due =  history[-1] + datetime.timedelta(seconds=period)
        self.comment = comment
        self.category = category
        self.thingID = thingID
        print(self.thingID, self.name, self.period, self.due, self.comment, self.category)
    
    def check(self, time=datetime.datetime.now(), *otherTimes):
        if isinstance(time, datetime.datetime):
            self.history.append(time)
        elif isinstance(time, tuple):
            try:
                y, m, d = time
                time = datetime.datetime(y,m,d)
                self.history.append(time)
            except:
                print("Could not parse time of {}".format(time))
        latest = time
        if len(otherTimes) > 0:
            for timestamp in otherTimes:
                if isinstance(timestamp, datetime.datetime):
                    self.history.append(timestamp)
                    if timestamp > time: 
                        latest = timestamp
                elif isinstance(timestamp, tuple):
                    try:
                        y, m, d = timestamp
                        timestamp = datetime.datetime(y,m,d)
                        self.history.append(timestamp)
                        if timestamp > time:
                            latest = timestamp
                    except:
                        print("Could not parse time of {}".format(timestamp))
        newdue = latest + datetime.timedelta(seconds=self.period)
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