/**
 * 
 */
package org.kumar.marcus;

/**
 * 
 */
public class MyArrayStack<T> implements MyStack<T> {
	    private T[] stack;
	    private int top;
	    private int capacity;

	    // Constructor to initialize stack
	    @SuppressWarnings("unchecked")
	    public MyArrayStack(int size) {
	        capacity = size;
	        stack = (T[]) new Object[capacity];
	        top = -1;
	    }

	    // Push element onto stack
	    @Override
	    public void push(T item) {
	        if (top == capacity - 1) {
	            System.out.println("Stack Overflow! Cannot push: " + item);
	            return;
	        }
	        stack[++top] = item;
	        System.out.println(item + " pushed to stack.");
	    }

	    // Pop element from stack
	    @Override
	    public T pop() {
	        if (isEmpty()) {
	            System.out.println("Stack Underflow! Cannot pop.");
	            return null;
	        }
	        T item = stack[top--];
	        System.out.println(item + " popped from stack.");
	        return item;
	    }

	    // Check if stack is empty
	    @Override
	    public boolean isEmpty() {
	        return top == -1;
	    }

	    // Get current size of stack
	    @Override
	    public int size() {
	        return top + 1;
	    }
	}