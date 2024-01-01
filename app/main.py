import toga

def add_task(widget):
    # Function to add a task to the list
    task = entry.value.strip()
    if task:
        tasks.append(task)
        list_view.data = tasks
        entry.value = ''

def build(app):
    global entry, list_view, tasks

    # Create a main box layout
    box = toga.Box()

    tasks = []  # List to store tasks
    
    # Create an entry for adding tasks
    entry = toga.TextInput()
    entry.style.flex = 1
    entry.placeholder = 'Enter a task'

    # Create a button to add tasks
    button = toga.Button('Add Task', on_press=add_task)

    # Create a list view to display tasks
    list_view = toga.List(data=tasks, style=Pack(flex=1))

    # Add entry and button to the main box layout
    box.add(entry)
    box.add(button)

    # Add listbox to the main box layout
    box.add(list_view)

    return box

def main():
    return toga.App("ToDo", "org.enabwf.todolist", startup=build)

if __name__ == '__main__':
    app = main()
    app.main_loop()

