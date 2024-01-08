import toga
from toga.constants import COLUMN, ROW
from toga.style import Pack
import datetime
from constants import *
from utils import *

from Task import Task
from TaskList import new_task_list_from_db

class TogaToDoList(toga.App):
    def startup(self):
        # Create the main window
        self.main_window = toga.MainWindow()
        global tasks, list_table

        tasks = new_task_list_from_db()
        self.tasks = tasks


        def on_row_double_click(widget, row):
            # Get the selected row data
            selected_row_index = self.list_table.data.index(row)  #TODO: Should this functionality be broken out? 
            selected_task = self.tasks.get_priority_list()[selected_row_index]
            selected_task.check(datetime.datetime.now())
            self.list_table.data = [(task.name, task.print_till_due(), format_period(task.period)) for task in tasks.get_priority_list()]
            self.list_table.refresh()
            list_container.refresh()
            self.main_window.info_dialog(
                f"Update {selected_task.name} successful", 
                f"The task has been updated and is now due at {selected_task.due} ({selected_task.print_till_due()})")

        self.list_source = self.tasks.source
        list_table = toga.Table(
            headings=["Task", "Due", "Period"],
            data=[
                (task.name, task.print_till_due(), format_period(task.period)) for task in self.tasks.get_priority_list()
            ],
            on_activate=on_row_double_click  # This handles the double-click event
         )
        self.list_table = list_table

        list_container = toga.OptionContainer(
            content=[
                ("Table", self.list_table)
            ]
        )

        # right_content = toga.Box(style=Pack(direction=COLUMN))
        # right_content.add(
        #     toga.Button(
        #         "FK", 
        #         on_press=self.button_handler,
        #         style=Pack(padding=20)
        #     )
        # )    

        # right_container = toga.ScrollContainer()

        # right_container.content = right_content

        # split = toga.SplitContainer()

        # split.content = [list_container, right_container]


        cmd1 = toga.Command(
            self.add_task_dialog,
            "Add task",
            tooltip="Add a new recurring task",
            icon="resources/brutus",
        )
        cmd2 = toga.Command(
            self.remove_task,
            "Remove task",
            tooltip="Remove selected task",
            icon=toga.Icon.DEFAULT_ICON,
        )
        cmd3 = toga.Command(
            self.edit_task,
            "Edit task",
            tooltip="Edit selected task",
            icon=toga.Icon.DEFAULT_ICON,
        )

        self.main_window.toolbar.add(cmd1, cmd2, cmd3)

        self.main_window.content = list_container

        # Show the main window
        self.main_window.show()

    def refresh_list_table(self):
        self.tasks.refresh_source()
        self.list_source = self.tasks.source
        self.list_table.data = self.list_source
        self.list_table.refresh()


    def button_handler(self, widget):
        print("button press")
        for i in range(0, 10):
            yield 1
            print("still running... (iteration %s)" % i)

    async def add_task_dialog(self, widget):
        def on_dialog_submit(widget):
            # Get the input values from the dialog
            task_name = name_input.value
            task_period_number = period_input.value
            task_period_unit= period_dropdown.value
            mydict={'Hours': ONE_HOUR, 'Days':ONE_DAY, 'Weeks':ONE_WEEK, 'Months':ONE_MONTH}
            
            task_comment = comment_input.value
            task_category = category_input.value

            if not task_name:
                self.main_window.error_dialog('Error', 'Please enter a task name')
                return
            if not task_period_number: 
                self.main_window.error_dialog('Error', 'Please enter a valid positive number for the period')
                return
            else: 
                try:
                    task_period_number = float(task_period_number)
                    if task_period_number <= 0: 
                        self.main_window.error_dialog('Error', 'Please enter a valid positive number for the period')
                        return
                except:
                    self.main_window.error_dialog('Error', 'Could not convert period to float')
                    return
            task_period = int(task_period_number * mydict[task_period_unit])

            # Create the new task based on the input values
            print(f"task name: {task_name}, {task_period_number}, {task_period_unit}, {task_period}, {task_comment}, {task_category}")
            new_task = Task(name=task_name, period=task_period, comment=task_comment, category=task_category)

            self.tasks.add(new_task)
            print(f"new task added! {new_task.name}, {new_task.period}, {new_task.due}")
            self.list_table.data = [(task.name, task.print_till_due(), format_period(task.period)) for task in self.tasks.get_priority_list()]
            self.list_table.refresh()

            # Close the dialog
            dialog.close()


        # Create input widgets for the dialog
        # name_input = toga.TextInput(placeholder='Enter Task Name')
        # period_input = toga.TextInput(placeholder='7')
        # period_units = ['Hours', 'Days', 'Weeks', 'Months']
        # period_dropdown = toga.Selection(items=period_units)
        # comment_input = toga.TextInput(placeholder='Enter Task Comment')
        # category_input = toga.TextInput(placeholder='Enter Task Category')

        # # Create a box layout for the dialog content
        # content = toga.Box(
        #     children=[
        #         name_input,
        #         period_input,
        #         period_dropdown,
        #         comment_input,
        #         category_input,
        #         toga.Button('Submit', on_press=on_dialog_submit)
        #     ],
        #     style=Pack(direction=COLUMN)  # Arrange children vertically
        # )
        name_label = toga.Label('Task Name')
        name_input = toga.TextInput(placeholder='Enter Task Name')
        name_box = toga.Box(children=[name_label, name_input], style=Pack(direction=ROW))

        period_label = toga.Label('Period')
        period_input = toga.TextInput(placeholder='7')
        period_units = ['Hours', 'Days', 'Weeks', 'Months']
        period_dropdown = toga.Selection(items=period_units)        
        period_box = toga.Box(children=[period_label, period_input, period_dropdown], style=Pack(direction=ROW))

        comment_label = toga.Label('Comment')
        comment_input = toga.TextInput(placeholder='Enter Task Comment')
        comment_box = toga.Box(children=[comment_label, comment_input], style=Pack(direction=ROW))

        category_label = toga.Label('Category')
        category_input = toga.TextInput(placeholder='Enter Task Category')
        category_box = toga.Box(children=[category_label, category_input], style=Pack(direction=ROW))

        # Create a box layout for the dialog content
        content = toga.Box(
            children=[
                name_box,
                period_box,
                comment_box,
                category_box,
                toga.Button('Submit', on_press=on_dialog_submit)
            ],
            style=Pack(direction=COLUMN)  # Arrange children vertically
        )
        # Create and show the dialog
        dialog = toga.Window('Add New Task',size=(275,150))
        dialog.content=content
        dialog.show()

    def get_selected_task(self, selection):
        selected_row_index = self.list_table.data.index(selection)
        sorted_tasks = self.tasks.get_priority_list()
        selected_task = sorted_tasks[selected_row_index]
        return selected_task

    async def remove_task(self, widget):
        if self.list_table.selection is not None:
            selected_task = self.get_selected_task(self.list_table.selection)
            if await self.main_window.question_dialog("Delete task", f"Are you sure you want to delete the task named {selected_task.name}?"):            
                self.tasks.delete(selected_task)
                self.main_window.info_dialog("Task deleted", "task deleted")
            else:
                self.main_window.info_dialog(
                    "Delete task canceled", "OK I didn't delete anything"
                )
        else:
            self.main_window.info_dialog(
                "No task to delete", "You must select a task to delete"
            )
        self.list_table.data = [(task.name, task.print_till_due(), format_period(task.period)) for task in self.tasks.get_priority_list()]
        self.list_table.refresh()

    async def edit_task(self, widget):
        if self.list_table.selection is not None:
            selected_task = self.get_selected_task(self.list_table.selection)
            # TODO: Implement edit task dialog
            
            # if await self.main_window.question_dialog("Delete task", f"Are you sure you want to delete the task named {selected_task.name}?"):            
            #     self.tasks.delete(selected_task)
            #     self.main_window.info_dialog("Task deleted", "task deleted")
            # else:
            #     self.main_window.info_dialog(
            #         "Delete task canceled", "OK I didn't delete anything"
            #     )
        else:
            self.main_window.info_dialog(
                "No task to delete", "You must select a task to delete"
            )
        self.list_table.data = [(task.name, task.print_till_due(), format_period(task.period)) for task in self.tasks.get_priority_list()]
        self.list_table.refresh()        

def main():
    return TogaToDoList("ToDo", "org.enabwf.todolist")

if __name__ == "__main__":
    main().main_loop()