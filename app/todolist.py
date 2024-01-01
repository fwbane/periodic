import toga
from toga.constants import COLUMN
from toga.style import Pack
import datetime

from Task import Task
from TaskList import new_task_list_from_db

class TogaToDoList(toga.App):
    def startup(self):
        # Create the main window
        self.main_window = toga.MainWindow()

        tasks = new_task_list_from_db()
        sorted_tasks = tasks.get_priority_list()
        
        def on_row_double_click(widget, row):
            # Get the selected row data
            selected_row_index = list_table.data.index(row)
            selected_task = sorted_tasks[selected_row_index]
            selected_task.check(datetime.datetime.now())
            list_table.data = [(task.name, task.due, task.period) for task in sorted_tasks]
            list_table.refresh()


        list_table = toga.Table(
            headings=["Task", "Due", "Period"],
            data=[
                (str(t.name), str(t.due), str(t.period)) for t in sorted_tasks
            ],
            on_double_click=on_row_double_click  # This handles the double-click event
         )

        list_container = toga.OptionContainer(
            content=[
                ("Table", list_table)
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
            self.action1,
            "Action 1",
            tooltip="Perform action 1",
            icon="resources/brutus",
        )
        cmd2 = toga.Command(
            self.action2,
            "Action 2",
            tooltip="Perform action 2",
            icon=toga.Icon.DEFAULT_ICON,
        )

        self.main_window.toolbar.add(cmd1, cmd2)

        self.main_window.content = list_container

        # Show the main window
        self.main_window.show()

    def button_handler(self, widget):
        print("button press")
        for i in range(0, 10):
            yield 1
            print("still running... (iteration %s)" % i)

    def action1(self, widget):
        self.main_window.info_dialog("Toga", "THIS! IS! TOGA!!")

    async def action2(self, widget):
        if await self.main_window.question_dialog("Toga", "Is this cool or what?"):
            self.main_window.info_dialog("Happiness", "I know, right! :-)")
        else:
            self.main_window.info_dialog(
                "Shucks...", "Well aren't you a spoilsport... :-("
            )

def main():
    return TogaToDoList("ToDo", "org.enabwf.todolist")

if __name__ == "__main__":
    main().main_loop()