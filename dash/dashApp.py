import dash
import dash_core_components as dcc
import dash_html_components as html
import plotly.graph_objs as go
import simple_network as sn

num_nodes = 100
sn = sn.SimpleNetwork()
data = sn.create_simple_graph(num_nodes)

fig = go.Figure(data,
                layout=go.Layout(
                    title='<br>Network Graph of ' + str(num_nodes) + ' rules',
                    titlefont=dict(size=16),
                    showlegend=False,
                    hovermode='closest',
                    margin=dict(b=20, l=5, r=5, t=40),
                    annotations=[dict(
                        showarrow=False,
                        xref="paper", yref="paper",
                        x=0.005, y=-0.002)],
                    xaxis=dict(showgrid=False, zeroline=False, showticklabels=False),
                    yaxis=dict(showgrid=False, zeroline=False, showticklabels=False)))

app = dash.Dash()
colors = {
    'background': '#111111',
    'text': '#7FDBFF'
}

app.layout = html.Div(html.Div(dcc.Graph(id='Graph',figure=fig)))

if __name__ == '__main__':
    app.run_server(debug=True)
